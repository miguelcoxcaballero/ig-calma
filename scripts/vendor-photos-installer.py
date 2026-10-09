"""Reuse Photos' installer verbatim, replacing only its Flutter host connection."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
source=(root/'vendor/inhouse-photos/UpdateInstaller.kt').read_text()
imports=source[source.index('import android.content.Intent'):source.index('class UpdateInstaller(')]
imports='\n'.join(line for line in imports.splitlines() if not line.startswith('import io.flutter.'))
body=source[source.index('  private fun installUpdate('):source.index('  private fun publishDownloadProgress(')]
body=body.replace('private fun installUpdate(', 'fun installUpdate(').replace('MethodChannel.Result','Result').replace('.update-provider','.fileprovider')
tail=source[source.index('  private fun validateUrl('):]
header='''package es.calma.instagram
import android.app.Activity
'''+imports+'''
// Original download/install/verification from Inhouse Photos; Flutter host adapted only.
class PhotosUpdateInstaller(private val activity: Activity, private val listener: Listener) {
  interface Listener { fun progress(downloadedBytes: Long, totalBytes: Long); fun stage(value: String) }
  interface Result { fun success(value: String); fun error(code: String, message: String?, details: Any?) }
'''
callbacks='''  private fun publishDownloadProgress(downloadedBytes: Long, totalBytes: Long?) {
    activity.runOnUiThread { listener.progress(downloadedBytes, totalBytes ?: -1L) }
  }
  private fun publishStage(stage: String) {
    activity.runOnUiThread { listener.stage(stage) }
  }
'''
(root/'app/src/es/calma/instagram/PhotosUpdateInstaller.kt').write_text(header+body+callbacks+tail)
