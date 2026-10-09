#!/usr/bin/env python3
"""Compare logged-out native startup on Google's official accelerated emulator.

Transport metadata in native/runtime-input/transport.json contains sourceCommit,
version, apkSizeBytes, apkSha256 and parts [{name, bytes, sha256}]. No credentials,
signing keys, releases or update-channel manifests are modified by this check.
Optional stock-transport.json carries the original APKM with the same hash/size
and part fields, checked against stock.lock.json before installing its signed splits.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import traceback
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = 'es.calma.instagram'
BASELINE_URL = ('https://github.com/miguelcoxcaballero/ig-calma/releases/download/'
                'v0.4.2/IG-Calma-0.4.2.apk')
BASELINE_SHA = 'e204620481f53c88c2f6f1413db1475d6853de12c451e13ed2bfde7a68ff8221'
BASELINE_SIZE = 146244269
SIGNER_SHA = '7133a4b3e4b9f9c4d2fbbd38b7dd2d44c7b8d0d7d16568bfe3da74a09e7887b6'
CANDIDATE_VERSION = '0.4.3'


def sha(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def run(command: list[str | Path], timeout: int = 60, **kwargs) -> subprocess.CompletedProcess:
    return subprocess.run(list(map(str, command)), text=True, capture_output=True,
                          timeout=timeout, **kwargs)


def save_result(path: Path, result: subprocess.CompletedProcess) -> None:
    path.write_text(f'exitCode={result.returncode}\n{result.stdout}\n{result.stderr}')


def require(result: subprocess.CompletedProcess, message: str) -> None:
    if result.returncode:
        raise RuntimeError(f'{message}: exit {result.returncode}\n{result.stdout}\n{result.stderr}')


def candidate_apk(work: Path) -> tuple[Path, dict]:
    source = ROOT / 'native/runtime-input'
    metadata = json.loads((source / 'transport.json').read_text())
    if metadata['version'] != CANDIDATE_VERSION or not re.fullmatch(r'[0-9a-f]{40}', metadata['sourceCommit']):
        raise RuntimeError('Unexpected candidate version/source commit')
    filename = f'IG-Calma-{CANDIDATE_VERSION}.apk'
    apk = work / filename
    parts = metadata['parts']
    if not parts or len(parts) > 20:
        raise RuntimeError('Unexpected transport part count')
    with apk.open('wb') as output:
        for index, part in enumerate(parts):
            if part['name'] != f'{filename}.part{index:03d}':
                raise RuntimeError('Transport part name/order differs')
            path = source / part['name']
            if path.stat().st_size != part['bytes'] or sha(path) != part['sha256']:
                raise RuntimeError('Corrupt transport part: ' + part['name'])
            with path.open('rb') as stream:
                shutil.copyfileobj(stream, output)
    if apk.stat().st_size != metadata['apkSizeBytes'] or sha(apk) != metadata['apkSha256']:
        raise RuntimeError('Reconstructed candidate hash/size differs')
    return apk, metadata


def stock_apks(work: Path) -> tuple[list[Path], dict] | None:
    """Optional unmodified, original-signature control for emulator compatibility."""
    source = ROOT / 'native/runtime-input'
    path = source / 'stock-transport.json'
    if not path.is_file():
        return None
    metadata = json.loads(path.read_text())
    lock = json.loads((ROOT / 'native/stock.lock.json').read_text())
    if metadata['apkSha256'] != lock['apkmSha256']:
        raise RuntimeError('Stock transport does not match the pinned original APKM')
    apkm = work / 'instagram-stock.apkm'
    parts = metadata['parts']
    if not parts or len(parts) > 20:
        raise RuntimeError('Unexpected stock transport part count')
    with apkm.open('wb') as output:
        for index, part in enumerate(parts):
            if part['name'] != f'instagram-stock.apkm.part{index:03d}':
                raise RuntimeError('Stock transport part name/order differs')
            chunk = source / part['name']
            if chunk.stat().st_size != part['bytes'] or sha(chunk) != part['sha256']:
                raise RuntimeError('Corrupt stock transport part: ' + part['name'])
            with chunk.open('rb') as stream:
                shutil.copyfileobj(stream, output)
    if apkm.stat().st_size != metadata['apkSizeBytes'] or sha(apkm) != lock['apkmSha256']:
        raise RuntimeError('Reconstructed stock APKM hash/size differs')
    names = ['base.apk', 'split_config.xxhdpi.apk']
    if set(lock['splits']) != set(names):
        raise RuntimeError('The pinned stock split set changed')
    splits = []
    destination = work / 'stock-splits'
    destination.mkdir(exist_ok=True)
    with zipfile.ZipFile(apkm) as archive:
        for name in names:
            if archive.namelist().count(name) != 1:
                raise RuntimeError('Missing or duplicate stock split: ' + name)
            split = destination / name
            with archive.open(name) as stream, split.open('wb') as output:
                shutil.copyfileobj(stream, output)
            if sha(split) != lock['splits'][name]:
                raise RuntimeError('Original stock split hash differs: ' + name)
            splits.append(split)
    return splits, lock


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--api', type=int, choices=[30, 33, 34, 35, 36], required=True)
    args = parser.parse_args()
    evidence = ROOT / 'native/build/runtime-evidence'
    evidence.mkdir(parents=True, exist_ok=True)
    work = ROOT / 'native/build/runtime-apks'
    work.mkdir(parents=True, exist_ok=True)
    report = {'api': args.api, 'stock': {'attempted': False},
              'baseline': {'attempted': False}, 'candidate': {'attempted': False}}
    emulator = None
    emulator_log = None
    adb = None

    def device(*arguments: str, timeout: int = 60) -> subprocess.CompletedProcess:
        return run([adb, '-s', 'emulator-5554', *arguments], timeout=timeout)

    def snapshot(directory: Path, name: str) -> None:
        # A missing UI dump must not stop log/PID collection after a crashed launch.
        for label, command in [('activities', ['shell', 'dumpsys', 'activity', 'activities']),
                               ('windows', ['shell', 'dumpsys', 'window', 'windows'])]:
            try:
                save_result(directory / f'{name}-{label}.txt', device(*command, timeout=20))
            except subprocess.TimeoutExpired as error:
                (directory / f'{name}-{label}.txt').write_text(str(error))
        try:
            result = subprocess.run([str(adb), '-s', 'emulator-5554', 'exec-out', 'screencap', '-p'],
                                    capture_output=True, timeout=20)
            if result.returncode == 0 and result.stdout.startswith(b'\x89PNG'):
                (directory / f'{name}.png').write_bytes(result.stdout)
            else:
                (directory / f'{name}-screenshot-error.txt').write_bytes(result.stderr)
        except subprocess.TimeoutExpired as error:
            (directory / f'{name}-screenshot-error.txt').write_text(str(error))
        try:
            result = device('shell', 'uiautomator', 'dump', '/sdcard/calma-startup.xml', timeout=20)
            save_result(directory / f'{name}-ui-dump.txt', result)
            if result.returncode == 0:
                save_result(directory / f'{name}-ui-pull.txt',
                            device('pull', '/sdcard/calma-startup.xml', str(directory / f'{name}.xml'), timeout=20))
        except subprocess.TimeoutExpired as error:
            (directory / f'{name}-ui-dump.txt').write_text(str(error))

    def launch(apk: Path | list[Path], label: str, updating: bool, package: str = PACKAGE) -> dict:
        directory = evidence / label
        directory.mkdir(exist_ok=True)
        result = {'attempted': True, 'package': package, 'updatingExistingInstall': updating, 'samples': []}
        try:
            device('shell', 'am', 'force-stop', package)
            device('logcat', '-b', 'all', '-c')
            installation = (device('install-multiple', '--no-incremental', '-r', *map(str, apk), timeout=90)
                            if isinstance(apk, list)
                            else device('install', '--no-incremental', '-r', str(apk), timeout=90))
            save_result(directory / 'install.txt', installation)
            result['installed'] = installation.returncode == 0 and 'Success' in installation.stdout
            if not result['installed']:
                return result
            resolved = device('shell', 'cmd', 'package', 'resolve-activity', '--brief',
                              '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LAUNCHER', package)
            save_result(directory / 'resolve-activity.txt', resolved)
            components = [line.strip() for line in resolved.stdout.splitlines()
                          if re.fullmatch(r'[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+', line.strip())]
            if resolved.returncode or len(components) != 1:
                raise RuntimeError('Cannot resolve the installed launcher activity')
            result['component'] = components[0]
            started = time.monotonic()
            launch_result = device('shell', 'am', 'start', '-W', '-n', components[0], timeout=60)
            save_result(directory / 'launch.txt', launch_result)
            result['launchExitCode'] = launch_result.returncode
            result['launchAccepted'] = launch_result.returncode == 0 and 'Status: ok' in launch_result.stdout
            for elapsed in [5, 15, 30, 45]:
                remaining = elapsed - (time.monotonic() - started)
                if remaining > 0:
                    time.sleep(remaining)
                pid = device('shell', 'pidof', package, timeout=10)
                result['samples'].append({'seconds': round(time.monotonic() - started, 1),
                                          'pids': pid.stdout.strip(), 'alive': pid.returncode == 0 and bool(pid.stdout.strip())})
            snapshot(directory, 'after-launch')
            ui = directory / 'after-launch.xml'
            if ui.is_file():
                result['visibleText'] = list(dict.fromkeys(
                    node.get('text', '') or node.get('content-desc', '')
                    for node in ET.parse(ui).iter('node')
                    if node.get('text') or node.get('content-desc')))
            final_pid = device('shell', 'pidof', package, timeout=10)
            result['finalPid'] = final_pid.stdout.strip()
            result['survived'] = bool(result['finalPid']) and all(
                sample['alive'] and sample['pids'] == result['finalPid'] for sample in result['samples'])
            save_result(directory / 'package.txt', device('shell', 'dumpsys', 'package', package))
        except Exception as error:
            result['error'] = str(error)
            (directory / 'error.txt').write_text(traceback.format_exc())
        finally:
            if not (directory / 'after-launch-windows.txt').exists():
                snapshot(directory, 'after-launch')
            try:
                logs = device('logcat', '-b', 'all', '-d', '-v', 'threadtime', timeout=30)
                save_result(directory / 'logcat.txt', logs)
                package_pattern = re.escape(package)
                result['appCrashLogged'] = bool(re.search(
                    rf'Process: {package_pattern}(?:[,\s:]|$)|>>> {package_pattern}(?:\s|:)|'
                    rf'am_crash.*{package_pattern}|ANR in {package_pattern}(?:\s|:|$)', logs.stdout))
                fatal_lines = [line for line in logs.stdout.splitlines()
                               if re.search(r'FATAL EXCEPTION|Fatal signal|Abort message|am_crash|ANR in', line)]
                (directory / 'fatal-lines.txt').write_text('\n'.join(fatal_lines))
                lines = logs.stdout.splitlines()
                excerpts = []
                for index, line in enumerate(lines):
                    if 'FATAL EXCEPTION' in line or 'Abort message:' in line or 'ANR in ' + package in line:
                        excerpts.extend(lines[max(0, index - 2):index + 45])
                result['crashExcerpt'] = '\n'.join(excerpts)[-18000:]
                # Native signals do not necessarily include an AndroidRuntime
                # exception or an abort message. Keep the dedicated crash buffer.
                result['crashBuffer'] = device('logcat', '-b', 'crash', '-d', '-v', 'threadtime', timeout=30).stdout[-50000:]
                result['appErrors'] = '\n'.join(line for line in lines
                    if re.search(r' [EW] ', line) and re.search(
                        rf'ACRA|AndroidRuntime|{package_pattern}|ClassNotFound|VerifyError|NoSuch|Exception|SoLoader|DexLibLoader|dextricks', line))[-25000:]
            except Exception as error:
                result['logcatError'] = str(error)
            result['passed'] = bool(result.get('installed') and result.get('launchAccepted')
                                    and result.get('survived') and not result.get('appCrashLogged')
                                    and not result.get('error') and not result.get('logcatError'))
            (directory / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
        return result

    try:
        candidate, transport = candidate_apk(work)
        report['candidateTransport'] = transport
        stock = stock_apks(work)
        if stock is not None:
            report['stockSha256'] = stock[1]['apkmSha256']
        baseline = work / 'IG-Calma-0.4.2.apk'
        with urllib.request.urlopen(BASELINE_URL, timeout=60) as response, baseline.open('wb') as output:
            shutil.copyfileobj(response, output)
        if baseline.stat().st_size != BASELINE_SIZE or sha(baseline) != BASELINE_SHA:
            raise RuntimeError('Public 0.4.2 download differs from pinned release')
        report['baselineSha256'] = BASELINE_SHA
        sdk = Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME', ''))
        if not str(sdk) or not (sdk / 'cmdline-tools/latest/bin/sdkmanager').is_file():
            raise RuntimeError('Official Android SDK command-line tools are unavailable')
        descriptor = os.open('/dev/kvm', os.O_RDWR)
        os.close(descriptor)
        sdkmanager = sdk / 'cmdline-tools/latest/bin/sdkmanager'
        avdmanager = sdk / 'cmdline-tools/latest/bin/avdmanager'
        image = f'system-images;android-{args.api};google_apis;x86_64'
        licenses = run([sdkmanager, '--licenses'], input='y\n' * 100, timeout=120)
        save_result(evidence / 'sdk-licenses.txt', licenses)
        require(licenses, 'SDK licenses')
        packages = run([sdkmanager, 'platform-tools', 'emulator', 'build-tools;36.0.0', image], timeout=360)
        save_result(evidence / 'sdk-install.txt', packages)
        require(packages, 'Official SDK package installation')
        build_tools = sdk / 'build-tools/36.0.0'
        for label, apk in [('baseline', baseline), ('candidate', candidate)]:
            verified = run([build_tools / 'apksigner', 'verify', '--verbose', '--print-certs', apk])
            save_result(evidence / f'{label}-signature.txt', verified)
            require(verified, label + ' APK signature')
            if 'Signer #1 certificate SHA-256 digest: ' + SIGNER_SHA not in verified.stdout:
                raise RuntimeError('Unexpected APK signing certificate: ' + label)
            save_result(evidence / f'{label}-badging.txt', run([build_tools / 'aapt', 'dump', 'badging', apk]))
        if stock is not None:
            stock_signer = stock[1]['signerSha256']['24-32' if args.api <= 32 else '33+']
            for split in stock[0]:
                verified = run([build_tools / 'apksigner', 'verify', '--verbose', '--print-certs',
                                '--min-sdk-version', str(args.api), '--max-sdk-version', str(args.api), split])
                save_result(evidence / f'stock-{split.stem}-signature.txt', verified)
                require(verified, 'Original stock signature: ' + split.name)
                # Rotated v3.1 signers use a minSdkVersion/maxSdkVersion label,
                # while the older signer is printed as "Signer #1".
                if 'certificate SHA-256 digest: ' + stock_signer not in verified.stdout:
                    raise RuntimeError('Original stock certificate differs: ' + split.name)
                save_result(evidence / f'stock-{split.stem}-badging.txt',
                            run([build_tools / 'aapt', 'dump', 'badging', split]))
        name = f'calma-api-{args.api}'
        # New command-line tools and emulator releases can otherwise choose
        # different XDG/default directories for the same generated AVD.
        android_user = work / 'android-user'
        avd_home = android_user / 'avd'
        avd_home.mkdir(parents=True, exist_ok=True)
        os.environ['ANDROID_USER_HOME'] = str(android_user)
        os.environ['ANDROID_AVD_HOME'] = str(avd_home)
        avd = run([avdmanager, 'create', 'avd', '--force', '--name', name, '--package', image,
                   '--device', 'pixel_2', '--path', avd_home / (name + '.avd')], input='no\n', timeout=60)
        save_result(evidence / 'avd-create.txt', avd)
        require(avd, 'AVD creation')
        adb = sdk / 'platform-tools/adb'
        require(run([adb, 'start-server']), 'ADB server startup')
        emulator_log = (evidence / 'emulator.txt').open('w')
        emulator = subprocess.Popen([str(sdk / 'emulator/emulator'), '-avd', name, '-port', '5554',
                                     '-accel', 'on', '-no-window', '-no-audio', '-no-boot-anim',
                                     '-no-snapshot', '-no-metrics', '-gpu', 'swiftshader', '-cores', '2',
                                     '-memory', '3072', '-camera-back', 'none', '-camera-front', 'none'],
                                    stdout=emulator_log, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + 300
        while time.monotonic() < deadline:
            if emulator.poll() is not None:
                raise RuntimeError('Emulator exited before boot completion')
            try:
                boot = device('shell', 'getprop', 'sys.boot_completed', timeout=10)
            except subprocess.TimeoutExpired:
                continue
            if boot.returncode == 0 and boot.stdout.strip() == '1':
                break
            time.sleep(2)
        else:
            raise RuntimeError('Emulator did not report sys.boot_completed=1 within 300 seconds')
        report['bootCompleted'] = True
        properties = device('shell', 'getprop')
        save_result(evidence / 'device-properties.txt', properties)
        abi = device('shell', 'getprop', 'ro.product.cpu.abilist').stdout.strip()
        report['abiList'] = abi
        if 'arm64-v8a' not in abi.split(','):
            raise RuntimeError('Official image does not expose ARM64 translation; native APK test is unsupported')
        device('shell', 'input', 'keyevent', '82')
        device('shell', 'wm', 'dismiss-keyguard')
        if stock is not None:
            report['stock'] = launch(stock[0], 'stock-original', updating=False,
                                     package=stock[1]['package'])
            device('shell', 'am', 'force-stop', stock[1]['package'])
        report['baseline'] = launch(baseline, 'baseline-0.4.2', updating=False)
        # Always try the candidate, including when baseline startup or installation fails.
        device('shell', 'am', 'force-stop', PACKAGE)
        report['candidate'] = launch(candidate, 'candidate-0.4.3',
                                     updating=bool(report['baseline'].get('installed')))
        if not report['candidate'].get('passed'):
            # This is an empty test device. Distinguish a failed upgrade from a
            # failure that also happens on a fresh install, without changing users' data.
            require(device('uninstall', PACKAGE), 'Remove failed test installation')
            report['candidateClean'] = launch(candidate, 'candidate-clean-0.4.3', updating=False)
        smoke = ROOT / 'native/runtime-input/Calma-Native-Smoke.apk'
        if smoke.is_file() and report['candidate'].get('passed'):
            expected = json.loads((ROOT / 'native/runtime-input/transport.json').read_text())['modelSmokeSha256']
            if sha(smoke) != expected:
                raise RuntimeError('Native model test APK hash differs')
            require(device('install', '-r', '-t', smoke, timeout=90), 'Install native model test')
            test = device('shell', 'am', 'instrument', '-w', 'es.calma.smoke/es.calma.smoke.NativeModelSmoke', timeout=120)
            save_result(evidence / 'native-model-smoke.txt', test)
            report['nativeModelSmoke'] = {'passed': test.returncode == 0 and 'CALMA_NATIVE_MODELS_PASSED' in test.stdout,
                                          'output': test.stdout + test.stderr}
            print('Native model smoke: ' + test.stdout + test.stderr, flush=True)
            if not report['nativeModelSmoke']['passed']:
                return 1
        return 0 if report['candidate'].get('passed') else 1
    except Exception as error:
        report['environmentError'] = str(error)
        (evidence / 'error.txt').write_text(traceback.format_exc())
        return 1
    finally:
        if emulator_log is not None:
            emulator_log.flush()
            emulator_text = (evidence / 'emulator.txt').read_text(errors='replace')
            print('Emulator output:\n' + emulator_text[-12000:], flush=True)
        (evidence / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps(report, indent=2), flush=True)
        if emulator is not None and emulator.poll() is None:
            try:
                device('emu', 'kill', timeout=10)
                emulator.wait(timeout=10)
            except Exception:
                emulator.kill()
                emulator.wait(timeout=10)
        if emulator_log is not None:
            emulator_log.close()


if __name__ == '__main__':
    raise SystemExit(main())
