"""Behavior tests against browser DOM fixtures, without Instagram credentials."""
from pathlib import Path
from playwright.sync_api import sync_playwright

SCRIPT = Path(__file__).resolve().parents[1].joinpath('app/assets/filter.js').read_text()

def post(user, key, kind='p', header=True):
    return f'<article id="{key}"><header>{f"<a href=\"/{user}/\">{user}</a>" if header else "Unknown"}</header><a href="/{kind}/{key}/">Time</a><div>Content {key}</div><button>Like</button></article>'

def fixture(posts):
    return '<nav><a href="/reels/">Reels</a><a href="/direct/inbox/">Inbox</a></nav><main><aside id="stories">Stories and suggestions</aside><section id="feed">' + posts + '</section><section id="discovery">More recommendations</section></main>'

def install(page, html, config):
    page.route('https://www.instagram.com/**', lambda route: route.fulfill(status=200, content_type='text/html', body=html))
    page.goto('https://www.instagram.com/')
    page.evaluate('(config) => window.CALMA_CONFIG=config', config)
    page.evaluate(SCRIPT)

def visible(page, selector):
    return page.locator(selector).is_visible()

with sync_playwright() as p:
    browser = p.chromium.launch(executable_path='/usr/bin/chromium', headless=True, args=['--no-sandbox'])
    page = browser.new_page(viewport={'width':390,'height':844})
    config = {'mode':1,'following':['alice'],'friends':['bob'],'reels':True,'limit':2}
    html = fixture(post('alice','a1') + post('bob','b1') + post('alice','r1','reel') + post('alice','unknown',header=False) + post('alice','a2') + post('alice','a3'))
    install(page, html, config)
    assert visible(page,'#a1') and visible(page,'#a2')
    for key in ['b1','r1','unknown','a3','stories','discovery']:
        assert not visible(page,'#'+key), key
    assert not visible(page,'nav a[href="/reels/"]')
    assert page.evaluate('window.__calma.count()') == 2
    assert 'límite de 2' in page.locator('#calma-end').inner_text()
    page.evaluate('(html) => document.querySelector("#feed").insertAdjacentHTML("beforeend",html)', post('alice','a4'))
    page.wait_for_timeout(300)
    assert not visible(page,'#a4'), 'Dynamic posts must obey cap'
    page.evaluate('history.pushState({},"","/bob/");window.__calma.scan()')
    assert visible(page,'#b1') and not page.locator('#calma-end').count(), 'Profiles must remain accessible'
    page.evaluate('history.pushState({},"","/");window.__calma.scan()')
    assert not visible(page,'#b1')
    page.evaluate('window.__calma.destroy()')
    assert visible(page,'#b1') and visible(page,'nav a[href="/reels/"]')
    config['mode']=2
    page.evaluate('(config) => window.CALMA_CONFIG=config', config)
    page.evaluate(SCRIPT)
    assert visible(page,'#b1') and not visible(page,'#a1'), 'Friends mode must use friends list'
    page.evaluate('window.__calma.destroy()')
    config['friends']=[]
    page.evaluate('(config) => window.CALMA_CONFIG=config', config)
    page.evaluate(SCRIPT)
    assert not visible(page,'#b1') and 'elige tus cuentas' in page.locator('#calma-end').inner_text()
    page.evaluate('window.__calma.destroy()')
    page.set_content('<main><form><input type="password"><button>Log in</button></form></main>')
    page.evaluate(SCRIPT)
    assert visible(page,'input[type="password"]') and not page.locator('#calma-end').count()
    page.evaluate('window.__calma.destroy()')
    config.update(mode=0,reels=False,limit=10)
    page.set_content(html)
    page.evaluate('(config) => window.CALMA_CONFIG=config', config)
    page.evaluate(SCRIPT)
    assert visible(page,'#r1') and visible(page,'#b1'), 'Off switches must restore content'
    print('PASS: allowlists, unknown authors, Reels, finite feed, dynamic loading, profiles, empty lists, login and disabling filters.')
    browser.close()
