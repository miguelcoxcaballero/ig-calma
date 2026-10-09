#!/usr/bin/env python3
"""Bind concise emulator startup evidence to the exact signed release APK.

Only summarized, allowlisted observations are published. Original CI artifacts
retain full diagnostics; this file never imports logcat, crash buffers or PID data.
Emulator startup does not establish physical-device or signed-in functionality.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import tempfile

ROOT = Path(__file__).resolve().parents[2]
BOOLEAN_FIELDS = ('attempted', 'installed', 'updatingExistingInstall', 'launchAccepted',
                  'survived', 'appCrashLogged', 'passed')


def read_json(path: Path) -> dict:
    value = json.loads(path.read_text())
    if not isinstance(value, dict):
        raise ValueError(f'Expected a JSON object: {path}')
    return value


def short_text(value: object, limit: int = 800) -> str:
    return ' '.join(str(value).split())[:limit]


def summarize_launch(value: dict, label: str) -> dict:
    if not isinstance(value, dict):
        raise ValueError(f'Invalid {label} launch result')
    result = {}
    for field in BOOLEAN_FIELDS:
        if field in value:
            if not isinstance(value[field], bool):
                raise ValueError(f'Invalid boolean {label}.{field}')
            result[field] = value[field]
    if value.get('passed'):
        if (any(value.get(field) is not True for field in
                ('attempted', 'installed', 'launchAccepted', 'survived'))
                or value.get('appCrashLogged') is not False
                or value.get('error') or value.get('logcatError')):
            raise ValueError(f'Contradictory successful launch: {label}')
        samples = value.get('samples', [])
        final_pid = value.get('finalPid')
        if (len(samples) < 2 or not final_pid
                or any(sample.get('alive') is not True or sample.get('pids') != final_pid for sample in samples)
                or samples[-1].get('seconds', 0) < 45):
            raise ValueError(f'Missing stable 45-second process observations: {label}')
    samples = value.get('samples', [])
    if samples:
        seconds = [sample.get('seconds') for sample in samples
                   if isinstance(sample, dict) and isinstance(sample.get('seconds'), (int, float))]
        if seconds:
            result['observedThroughSeconds'] = round(max(seconds), 1)
    texts = value.get('visibleText')
    if texts is not None:
        if not isinstance(texts, list) or any(not isinstance(text, str) for text in texts):
            raise ValueError(f'Invalid visible text: {label}')
        result['visibleText'] = [short_text(text, 200) for text in texts[:60]]
    for field in ('error', 'logcatError'):
        if value.get(field):
            result[field] = short_text(value[field])
    return result


def summarize_report(report: dict, manifest: dict, apk_sha: str, apk_size: int) -> dict:
    transport = report.get('candidateTransport', {})
    if (transport.get('apkSha256') != apk_sha
            or transport.get('apkSizeBytes') != apk_size
            or transport.get('version') != manifest['version']):
        raise ValueError('CI candidate transport differs from the APK/manifest; rerun the final APK')
    source_commit = transport.get('sourceCommit', '')
    if not re.fullmatch(r'[0-9a-f]{40}', source_commit):
        raise ValueError('CI report has no exact candidate source commit')
    api = report.get('api')
    if isinstance(api, bool) or not isinstance(api, int) or not 28 <= api <= 100:
        raise ValueError('CI report has an invalid Android API')
    result = {'api': api, 'sourceCommit': source_commit,
              'bootCompleted': report.get('bootCompleted') is True}
    if report.get('abiList'):
        result['abiList'] = short_text(report['abiList'], 200)
    for field in ('stock', 'baseline', 'candidate', 'candidateClean'):
        if field in report:
            result[field] = summarize_launch(report[field], f'API {api} {field}')
    for field in ('stockSha256', 'baselineSha256'):
        if field in report:
            if not isinstance(report[field], str) or not re.fullmatch(r'[0-9a-f]{64}', report[field]):
                raise ValueError(f'Invalid comparison APK hash: {field}')
            result[field] = report[field]
    smoke = report.get('nativeModelSmoke')
    if smoke is not None:
        if not isinstance(smoke, dict) or not isinstance(smoke.get('passed'), bool):
            raise ValueError('Invalid native model instrumentation result')
        result['nativeModelSmoke'] = {'passed': smoke['passed'], 'scope': 'Actual native end-model allocation, inline row, light/dark rendering, accessibility, recycling and four original selector models with remaining time and credit lock; no logged-in feed'}
        if 'real Media/dictionary/user/wrapper/response/parser models' in smoke.get('output', ''):
            result['nativeModelSmoke']['scope'] += '; actual Media, LiveTree dictionaries, User, wrapper, response and parser-context models with synthetic cached fields: cold main-thread head, cursor, cross-page deduplication and complete cache replay; no authenticated HTTP'
        if smoke['passed'] and 'CALMA_NATIVE_MODELS_PASSED' not in smoke.get('output', ''):
            raise ValueError('Native model success has no instrumentation marker')
    if report.get('environmentError'):
        result['environmentError'] = short_text(report['environmentError'])
    candidate = result.get('candidate', {})
    if candidate.get('passed') and (not result['bootCompleted'] or result.get('environmentError')):
        raise ValueError('Successful candidate contradicts emulator environment result')
    if result.get('environmentError') or not result['bootCompleted']:
        result['outcome'] = 'environment_error'
    elif smoke is not None and not smoke['passed']:
        result['outcome'] = 'native_model_smoke_failed'
    elif candidate.get('passed'):
        result['outcome'] = 'startup_passed'
    elif not candidate.get('attempted'):
        result['outcome'] = 'not_tested'
    elif result.get('stock', {}).get('attempted') and not result['stock'].get('passed'):
        result['outcome'] = 'candidate_failed_stock_also_failed'
    else:
        result['outcome'] = 'startup_failed'
    return result


def record(apk: Path, manifest_path: Path, reports: list[Path], ci_url: str,
           dist: Path, output_directory: Path) -> Path:
    if not re.fullmatch(r'https://github\.com/miguelcoxcaballero/ig-calma/actions/runs/\d+', ci_url):
        raise ValueError('Expected the repository GitHub Actions run URL')
    manifest = read_json(manifest_path)
    version = manifest.get('version', '')
    if not re.fullmatch(r'\d+\.\d+\.\d+', version):
        raise ValueError('Invalid manifest version')
    with apk.open('rb') as stream:
        apk_sha = hashlib.file_digest(stream, 'sha256').hexdigest()
    apk_size = apk.stat().st_size
    if manifest.get('apkSha256') != apk_sha or manifest.get('apkSizeBytes') != apk_size:
        raise ValueError('APK hash/size differs from the update manifest')
    if not reports:
        raise ValueError('At least one CI report is required')
    observations = [summarize_report(read_json(path), manifest, apk_sha, apk_size) for path in reports]
    observations.sort(key=lambda entry: entry['api'])
    if len({entry['api'] for entry in observations}) != len(observations):
        raise ValueError('Duplicate Android API reports; choose one run per API')
    runtime = {
        'scope': 'Logged-out startup on official Android Emulator with ARM64 translation',
        'ciUrl': ci_url,
        'apkSha256': apk_sha,
        'apkSizeBytes': apk_size,
        'physicalDeviceTested': False,
        'instagramAccountTested': False,
        'startupPassedOnApis': [entry['api'] for entry in observations if entry['outcome'] == 'startup_passed'],
        'observations': observations,
    }
    validation = {'schemaVersion': 1, 'version': version,
                  'recordedAt': datetime.now(timezone.utc).isoformat(timespec='seconds'),
                  'runtimeTesting': runtime}
    updates = {}
    for filename in ('build-info.json', 'verification.json'):
        path = dist / filename
        value = read_json(path)
        if value.get('calmaVersion') != version:
            raise ValueError(f'{filename} belongs to a different version')
        if filename == 'build-info.json' and value.get('apkSha256') != apk_sha:
            raise ValueError('build-info.json belongs to a different APK')
        if ('apkSha256' in value and value['apkSha256'] != apk_sha
                or 'apkSizeBytes' in value and value['apkSizeBytes'] != apk_size):
            raise ValueError(f'{filename} hash/size differs')
        version_code = value.get('calmaVersionCode' if filename == 'build-info.json' else 'versionCode')
        if version_code != manifest.get('versionCode'):
            raise ValueError(f'{filename} version code differs')
        value['runtimeTesting'] = runtime
        # Existing physical-device flags must not be promoted by an emulator run.
        if filename == 'verification.json':
            value['deviceTested'] = False
        if filename == 'build-info.json':
            value['deviceTesting'] = 'Physical device and Instagram account not tested; see runtimeTesting for emulator startup'
        updates[path] = value
    output = output_directory / (version + '.json')
    updates[output] = validation
    # Finish all validation before replacing any output. Each JSON replacement is atomic.
    for path, value in updates.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile('w', dir=path.parent, prefix='.' + path.name, delete=False) as stream:
            json.dump(value, stream, indent=2, ensure_ascii=False)
            stream.write('\n')
            temporary = Path(stream.name)
        temporary.replace(path)
    return output


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--report', type=Path, action='append', required=True)
    parser.add_argument('--ci-url', required=True)
    parser.add_argument('--manifest', type=Path, default=ROOT / 'native/android-update.json')
    parser.add_argument('--dist-dir', type=Path, help='Defaults to the APK directory')
    parser.add_argument('--output-dir', type=Path, default=ROOT / 'native/validation')
    args = parser.parse_args()
    output = record(args.apk, args.manifest, args.report, args.ci_url,
                    args.dist_dir or args.apk.parent, args.output_dir)
    print(f'Exact-APK runtime evidence recorded: {output}')


if __name__ == '__main__':
    main()
