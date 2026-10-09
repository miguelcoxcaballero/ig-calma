#!/usr/bin/env python3
"""Reject stale runtime evidence before changing release metadata."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('runtime_evidence', ROOT / 'native/scripts/record-runtime-check.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class RuntimeEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='calma-runtime-evidence-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.apk = self.root / 'candidate.apk'
        self.apk.write_bytes(b'signed APK fixture')
        self.digest = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        self.manifest = self.root / 'manifest.json'
        self.manifest.write_text(json.dumps({'version': '0.4.1', 'versionCode': 12,
                                            'apkSha256': self.digest, 'apkSizeBytes': self.apk.stat().st_size}))
        self.build = self.root / 'build-info.json'
        self.build.write_text(json.dumps({'calmaVersion': '0.4.1', 'calmaVersionCode': 12, 'apkSha256': self.digest}))
        self.verification = self.root / 'verification.json'
        self.verification.write_text(json.dumps({'calmaVersion': '0.4.1', 'versionCode': 12, 'deviceTested': False}))
        self.report = {'api': 36, 'bootCompleted': True, 'abiList': 'x86_64,arm64-v8a',
                       'candidateTransport': {'version': '0.4.1', 'apkSha256': self.digest,
                                              'apkSizeBytes': self.apk.stat().st_size, 'sourceCommit': 'a' * 40},
                       'stock': {'attempted': False}, 'baseline': {'attempted': False},
                       'candidate': {'attempted': True, 'installed': True, 'launchAccepted': True,
                                     'survived': True, 'appCrashLogged': False, 'passed': True,
                                     'updatingExistingInstall': True, 'finalPid': '1234',
                                     'samples': [{'seconds': second, 'pids': '1234', 'alive': True}
                                                 for second in [5, 15, 30, 45]],
                                     'visibleText': ['Instagram', 'Log in'],
                                     'crashBuffer': 'private raw diagnostics', 'appErrors': 'raw errors'}}
        self.source = self.root / 'report.json'

    def write(self, report=None):
        self.source.write_text(json.dumps(self.report if report is None else report))
        return module.record(self.apk, self.manifest, [self.source],
                             'https://github.com/miguelcoxcaballero/ig-calma/actions/runs/12345',
                             self.root, self.root / 'validation')

    def assert_rejected_without_changes(self, report):
        before = [self.build.read_bytes(), self.verification.read_bytes()]
        with self.assertRaises(ValueError):
            self.write(report)
        self.assertEqual(before, [self.build.read_bytes(), self.verification.read_bytes()])
        self.assertFalse((self.root / 'validation').exists())

    def test_exact_artifact_preserves_scope_and_omits_raw_diagnostics(self):
        output = self.write()
        value = json.loads(output.read_text())['runtimeTesting']
        self.assertEqual(value['apkSha256'], self.digest)
        self.assertEqual(value['startupPassedOnApis'], [36])
        self.assertFalse(value['physicalDeviceTested'])
        self.assertFalse(value['instagramAccountTested'])
        self.assertNotIn('private raw diagnostics', output.read_text())
        self.assertNotIn('crashBuffer', output.read_text())
        self.assertEqual(value, json.loads(self.build.read_text())['runtimeTesting'])
        self.assertFalse(json.loads(self.verification.read_text())['deviceTested'])

    def test_old_candidate_hash_is_rejected_before_writes(self):
        report = copy.deepcopy(self.report)
        report['candidateTransport']['apkSha256'] = '0' * 64
        self.assert_rejected_without_changes(report)

    def test_size_mismatch_is_rejected_before_writes(self):
        report = copy.deepcopy(self.report)
        report['candidateTransport']['apkSizeBytes'] += 1
        self.assert_rejected_without_changes(report)

    def test_restarted_process_cannot_be_recorded_as_success(self):
        report = copy.deepcopy(self.report)
        report['candidate']['samples'][0]['pids'] = '9999'
        self.assert_rejected_without_changes(report)

    def test_stock_and_candidate_failure_is_retained_without_unsupported_claim(self):
        report = copy.deepcopy(self.report)
        report['api'] = 30
        failed = {'attempted': True, 'installed': True, 'launchAccepted': True,
                  'survived': False, 'appCrashLogged': True, 'passed': False}
        report['stock'] = failed.copy()
        report['candidate'] = failed.copy()
        output = self.write(report)
        value = json.loads(output.read_text())['runtimeTesting']
        self.assertEqual(value['startupPassedOnApis'], [])
        self.assertEqual(value['observations'][0]['outcome'], 'candidate_failed_stock_also_failed')

    def test_unsupported_emulator_does_not_become_app_failure_or_success(self):
        report = copy.deepcopy(self.report)
        report['candidate'] = {'attempted': False}
        report['environmentError'] = 'Official image does not expose ARM64 translation'
        output = self.write(report)
        value = json.loads(output.read_text())['runtimeTesting']
        self.assertEqual(value['startupPassedOnApis'], [])
        self.assertEqual(value['observations'][0]['outcome'], 'environment_error')


if __name__ == '__main__':
    unittest.main()
