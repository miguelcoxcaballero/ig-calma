"""Download the official Android build tools and Eclipse Java compiler."""
import concurrent.futures
import pathlib
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent / 'tools'
ROOT.mkdir(exist_ok=True)
ITEMS = [
    ('platform.zip', 'https://dl.google.com/android/repository/platform-35_r02.zip'),
    ('build-tools.zip', 'https://dl.google.com/android/repository/build-tools_r35_linux.zip'),
    ('ecj.jar', 'https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.39.0/ecj-3.39.0.jar'),
]

def download(item):
    name, url = item
    target = ROOT / name
    urllib.request.urlretrieve(url, target)
    if name.endswith('.zip'):
        with zipfile.ZipFile(target) as archive:
            archive.extractall(ROOT)
    return name

with concurrent.futures.ThreadPoolExecutor() as pool:
    for name in pool.map(download, ITEMS):
        print('Descargado:', name)
for name in ['aapt', 'aapt2', 'd8', 'zipalign', 'apksigner']:
    (ROOT / 'android-15' / name).chmod(0o755)
