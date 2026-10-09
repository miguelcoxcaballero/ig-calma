"""Check that the updater already installed in 0.3.7 can discover this release."""
import json
from pathlib import Path
from urllib.parse import urlsplit
from zipfile import ZipFile

from playwright.sync_api import sync_playwright


ROOT = Path(__file__).resolve().parents[1]
MANIFEST_URL = "https://raw.githubusercontent.com/miguelcoxcaballero/ig-calma/main/android-update.json"
LATEST_URL = "https://api.github.com/repos/miguelcoxcaballero/ig-calma/releases/latest"
ASSET_ORIGIN = "https://appassets.androidplatform.net"
INSTALLED_VERSION = "0.3.7"

manifest = json.loads((ROOT / "android-update.json").read_text())
native_manifest = json.loads((ROOT / "native/android-update.json").read_text())
assert manifest == native_manifest, "The installed and native APK channels must announce the same release"
assert manifest["required"] is True, "Inhouse Read suppresses the popup when required is false"
assert tuple(map(int, manifest["version"].split("."))) > (0, 3, 7), "The installed 0.3.7 must see a newer release"
assert manifest["versionCode"] > 11

# These are the bytes users already have, which a source edit cannot repair.
with ZipFile(ROOT / "downloads/IG-Calma-0.3.7.apk") as apk:
    assets = {
        "/" + name.removeprefix("assets/"): apk.read(name)
        for name in apk.namelist()
        if name.startswith("assets/updates/") and not name.endswith("/")
    }
assert MANIFEST_URL.encode() in assets["/updates/updater.js"]


def exercise(browser, installed_version, quiet=False):
    context = browser.new_context()
    context.add_init_script("""
        window.installs = [];
        window.offers = [];
        window.InhouseNative = {
            getAppVersion() { return INSTALLED_VERSION; },
            installAppUpdate(url, sha) { window.installs.push({url, sha}); }
        };
        window.InhouseUpdateHost = {
            offer(value) { window.offers.push(JSON.parse(value)); }
        };
    """.replace("INSTALLED_VERSION", json.dumps(installed_version)))
    if quiet:
        # Startup must work before Instagram paints or schedules idle work.
        context.add_init_script("window.requestAnimationFrame=()=>0;window.requestIdleCallback=()=>0")

    requests = []
    unexpected = []

    def route(request):
        parsed = urlsplit(request.request.url)
        url = parsed._replace(query="", fragment="").geturl()
        requests.append(url)
        headers = {"Access-Control-Allow-Origin": "*"}
        if url == MANIFEST_URL:
            request.fulfill(content_type="application/json", headers=headers, body=json.dumps(manifest))
        elif url == LATEST_URL:
            # A prerelease is absent from /latest. The published manifest must
            # suffice even when this recovery channel has no stable release.
            request.fulfill(status=404, content_type="application/json", headers=headers, body="{}")
        elif f"{parsed.scheme}://{parsed.netloc}" == ASSET_ORIGIN and parsed.path in assets:
            mime = "application/javascript" if parsed.path.endswith(".js") else "text/css" if parsed.path.endswith(".css") else "text/html"
            request.fulfill(content_type=mime, body=assets[parsed.path])
        else:
            unexpected.append(request.request.url)
            request.abort()

    context.route("**/*", route)
    page = context.new_page()
    page.goto(ASSET_ORIGIN + "/updates/index.html?inhouse_app=1" + ("&quiet=1" if quiet else ""))
    if installed_version == manifest["version"]:
        page.wait_for_function("document.querySelector('#check-status').textContent.includes('última versión')", timeout=3000)
        assert page.locator("#android-update-gate").count() == 0
        assert page.evaluate("window.offers") == []
        assert page.evaluate("window.installs") == []
        assert LATEST_URL in requests, "The no-update result must survive a missing stable release"
    elif quiet:
        page.wait_for_function("window.offers.length > 0", timeout=2000, polling=25)
        assert page.evaluate("window.offers[0]") == manifest
        assert page.locator("main").is_hidden()
        assert page.evaluate("window.installs") == []
    else:
        page.wait_for_selector("#android-update-gate", timeout=3000)
        message = page.locator("[data-update-message]").inner_text()
        assert INSTALLED_VERSION in message and manifest["version"] in message
        page.locator("[data-update-install]").click()
        assert page.evaluate("window.installs") == [{"url": manifest["apkUrl"], "sha": manifest["apkSha256"]}]
    assert MANIFEST_URL in requests, "The preinstalled main channel must supply the release"
    assert not unexpected, f"Unexpected network requests: {unexpected}"
    context.close()


with sync_playwright() as playwright:
    browser = playwright.chromium.launch(executable_path="/usr/bin/chromium", headless=True, args=["--no-sandbox"])
    exercise(browser, INSTALLED_VERSION)
    exercise(browser, INSTALLED_VERSION, quiet=True)
    exercise(browser, manifest["version"])
    browser.close()

print("PASS: the shipped 0.3.7 APK discovers the published native release, offers it immediately at startup, passes its exact URL and SHA-256 to Photos, and never offers the same installed version; both publication channels agree without a stable GitHub Release.")
