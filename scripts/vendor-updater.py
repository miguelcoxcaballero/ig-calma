"""Mechanical bindings for the unmodified Inhouse Read updater kept in vendor/."""
from pathlib import Path

root=Path(__file__).resolve().parents[1]
vendor=root/'vendor/inhouse-read'
js=(vendor/'android-update.js').read_text()
js=js.replace("import { runAfterFirstFrame } from './idle-startup.js'",'')
js=js.replace('export function ','function ')
js=js.replace("const UPDATE_MANIFEST_PATH = 'android-update.json'","const UPDATE_MANIFEST_PATH = 'https://raw.githubusercontent.com/miguelcoxcaballero/ig-calma/main/android-update.json'")
js=js.replace('miguelcoxcaballero/inhouse-read','miguelcoxcaballero/ig-calma')
js=js.replace('inhouse-read-release-v${version}.apk','IG-Calma-${version}.apk').replace('android-v','v')
js=js.replace('inhouse read</div>','Instagram Calma</div>')
idle=(vendor/'idle-startup.js').read_text().replace('export function ','function ').replace('export async function ','async function ')
idle=idle.replace("'canvas.ihr-bookshelf-scene'","'[data-inhouse-update-screen]'")
bundle=idle+'\n'+js+'\nwindow.InhouseUpdater={initAndroidUpdateChecks,offerAvailableAndroidUpdate,compareSemanticVersions,validateManifest,shouldOfferUpdate,handleInhouseUpdateResult};\n'
(root/'app/assets/updates/updater.js').write_text(bundle)
(root/'app/assets/updates/android-update.css').write_bytes((vendor/'android-update.css').read_bytes())
