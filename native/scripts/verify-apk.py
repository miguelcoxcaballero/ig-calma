#!/usr/bin/env python3
"""Verify the built artifact, not merely successful patch execution."""
from __future__ import annotations

import argparse
from collections import Counter
import importlib.util
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
NATIVE = ROOT / 'native'
CALMA_SIGNER = '7133a4b3e4b9f9c4d2fbbd38b7dd2d44c7b8d0d7d16568bfe3da74a09e7887b6'


def output(*command: str | Path) -> str:
    return subprocess.check_output(list(map(str, command)), text=True)


def manifest_nodes(tree: str) -> list[dict]:
    nodes = []
    for line in tree.splitlines():
        element = re.match(r'\s*E: (\S+)', line)
        if element:
            nodes.append({'tag': element.group(1), 'attrs': {}})
            continue
        attribute = re.match(r'\s*A: (?:http://\S+:)?([^\s(=]+)(?:\(0x[^)]+\))?=(.*)', line)
        if attribute and nodes:
            key, raw = attribute.groups()
            if raw.startswith('"'):
                value = json.JSONDecoder().raw_decode(raw)[0]
            else:
                value = raw.split(' ', 1)[0]
            if key in nodes[-1]['attrs']:
                raise AssertionError(f'Duplicate manifest attribute: {key}')
            nodes[-1]['attrs'][key] = value
    return nodes


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--stock', type=Path, required=True)
    parser.add_argument('--build-tools', type=Path, required=True)
    args = parser.parse_args()
    stock_lock = json.loads((NATIVE / 'stock.lock.json').read_text())
    with args.stock.open('rb') as stream:
        assert hashlib.file_digest(stream, 'sha256').hexdigest() == stock_lock['splits']['base.apk'], 'Unverified stock baseline'
    config = (NATIVE / 'src/es/calma/instagram/nativeapp/CalmaBuild.java').read_text()
    version = re.search(r'VERSION = "([^"]+)"', config).group(1)
    code = re.search(r'VERSION_CODE = (\d+)', config).group(1)
    signature = output(args.build_tools / 'apksigner', 'verify', '--verbose', '--print-certs', args.apk)
    assert CALMA_SIGNER in signature, 'Signing identity changed; existing Calma installations could not update'
    output(args.build_tools / 'zipalign', '-c', '-P', '16', '4', args.apk)
    tree = output(args.build_tools / 'aapt2', 'dump', 'xmltree', args.apk, '--file', 'AndroidManifest.xml')
    nodes = manifest_nodes(tree)
    manifest = next(node['attrs'] for node in nodes if node['tag'] == 'manifest')
    assert manifest['package'] == 'es.calma.instagram', manifest
    assert manifest['versionCode'] == code, manifest
    assert manifest['versionName'] == '439.0.0.37.89', 'Stock network protocol version changed'
    assert 'split' not in manifest and manifest.get('isSplitRequired') not in ('true', '0xffffffff')
    app = next(node['attrs'] for node in nodes if node['tag'] == 'application')
    assert app['name'].startswith('com.instagram.'), 'Original native Application missing'
    assert app['label'] == 'Instagram Calma'
    assert not any(node['tag'] == 'uses-split' for node in nodes), 'Unmerged required split'
    metadata = {node['attrs'].get('name'): node['attrs'] for node in nodes if node['tag'] == 'meta-data'}
    assert metadata['es.calma.version']['value'] == version
    assert metadata.get('com.android.vending.splits.required', {}).get('value') not in ('true', '0xffffffff')
    providers = [node['attrs'] for node in nodes if node['tag'] == 'provider']
    authorities = [authority for provider in providers for authority in provider['authorities'].split(';')]
    original_nodes = manifest_nodes(output(args.build_tools / 'aapt2', 'dump', 'xmltree', args.stock, '--file', 'AndroidManifest.xml'))
    expected_authorities = []
    for node in original_nodes:
        if node['tag'] != 'provider':
            continue
        for authority in node['attrs']['authorities'].split(';'):
            if authority == 'com.instagram.android' or authority.startswith('com.instagram.android.'):
                expected_authorities.append('es.calma.instagram' + authority.removeprefix('com.instagram.android'))
            else:
                expected_authorities.append('es.calma.instagram.' + authority)
    expected_authorities += ['es.calma.instagram.calma.init', 'es.calma.instagram.fileprovider']
    # Instagram itself declares two initializers with the same authority; preserve
    # its exact provider set and reject newly introduced collisions or omissions.
    assert Counter(authorities) == Counter(expected_authorities), 'Provider authority mapping changed'
    assert all(authority.startswith('es.calma.instagram.') for authority in authorities), authorities
    assert 'es.calma.instagram.calma.init' in authorities and 'es.calma.instagram.fileprovider' in authorities
    own = {node['attrs'].get('name'): node['attrs'] for node in nodes}
    for component in ['es.calma.instagram.UpdateActivity', 'es.calma.instagram.nativeapp.CalmaSettingsActivity',
                      'es.calma.instagram.nativeapp.NativeInitProvider', 'es.calma.vendor.androidx.core.content.FileProvider']:
        assert own[component]['exported'] == 'false', f'Exposed private component: {component}'
    assert 'android.permission.REQUEST_INSTALL_PACKAGES' in own
    assert 'android.permission.POST_NOTIFICATIONS' in own
    assert any(node['tag'] == 'service' and 'push' in node['attrs'].get('name', '').lower() for node in nodes), 'Native push service missing'
    for node in nodes:
        for key in ('permission', 'readPermission', 'writePermission', 'taskAffinity'):
            assert not node['attrs'].get(key, '').startswith('com.instagram.android'), (node, key)
        if node['tag'] in ('permission', 'uses-permission'):
            assert not node['attrs'].get('name', '').startswith('com.instagram.android'), node
    spec = importlib.util.spec_from_file_location('extension', NATIVE / 'scripts/compile-extension.py')
    extension = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(extension)
    classes = set()
    with zipfile.ZipFile(args.apk) as built, zipfile.ZipFile(args.stock) as stock:
        for name in built.namelist():
            if re.fullmatch(r'classes\d*\.dex', name):
                part = extension.dex_classes(built.read(name))
                assert not (classes & part), f'Duplicate DEX classes in {name}'
                classes.update(part)
        stock_classes = set()
        for name in stock.namelist():
            if re.fullmatch(r'classes\d*\.dex', name):
                stock_classes.update(extension.dex_classes(stock.read(name)))
            if name.startswith('lib/') and name.endswith('.so'):
                assert built.read(name) == stock.read(name), f'Native library changed: {name}'
        assert not (stock_classes - classes), 'Original native classes missing'
        for name in ('NativeFeed', 'NativeRelations', 'CalmaReels', 'CalmaSettings', 'CalmaSettingsActivity',
                     'NativeUpdates', 'NativeInitProvider'):
            assert f'Les/calma/instagram/nativeapp/{name};' in classes, name
        assert 'Les/calma/instagram/MainActivity;' not in classes, 'Legacy web feed bundled accidentally'
        for name in ('index.html', 'android-update.css', 'updater.js'):
            expected = (ROOT / 'app/assets/updates' / name).read_bytes()
            if name == 'updater.js':
                expected = expected.replace(
                    b'https://raw.githubusercontent.com/miguelcoxcaballero/ig-calma/main/android-update.json',
                    b'https://raw.githubusercontent.com/miguelcoxcaballero/ig-calma/native-instagram/native/android-update.json')
            assert built.read('assets/updates/' + name) == expected, f'Inhouse asset changed: {name}'
        assert 'res/xml/calma_update_file_paths.xml' in built.namelist()
    component_tags = ('application', 'activity', 'service', 'receiver', 'provider')
    original_components = set()
    for node in original_nodes:
        keys = ('appComponentFactory', 'backupAgent', 'targetActivity') + (('name',) if node['tag'] in component_tags else ())
        for key in keys:
            name = node['attrs'].get(key)
            if name:
                if name.startswith('.'):
                    name = 'com.instagram.android' + name
                elif '.' not in name:
                    name = 'com.instagram.android.' + name
                original_components.add(name)
    lazy_components = set()
    for node in nodes:
        attr = node['attrs']
        names = [attr.get(key) for key in ('appComponentFactory', 'backupAgent', 'targetActivity')]
        if node['tag'] in component_tags:
            names.append(attr.get('name'))
        for name in filter(None, names):
            assert not name.startswith('.'), f'Unresolved original component: {name}'
            if not name.startswith('android.'):
                descriptor = 'L' + name.replace('.', '/') + ';'
                if descriptor not in classes:
                    # Stock's AppComponentFactory resolves its own lazyload aliases.
                    # Accept only names already absent from the verified stock DEX.
                    assert name in original_components and descriptor not in stock_classes, f'Missing component class: {name}'
                    lazy_components.add(name)
    report = {'signature': CALMA_SIGNER, 'calmaVersion': version, 'versionCode': int(code),
              'stockClassesPreserved': len(stock_classes), 'totalClasses': len(classes),
              'providerAuthorities': len(authorities), 'inhouseAssetsPreserved': True,
              'nativeLibrariesPreserved': True, 'originalLazyComponents': sorted(lazy_components),
              'alignment': '16 KB', 'deviceTested': False}
    (args.apk.parent / 'verification.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
