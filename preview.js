const screen = document.querySelector('#screen');
const switcher = document.querySelector('.switcher');
const previews = {
  charging: '充電檢測畫面，顯示電池端淨功率與趨勢圖',
  hardware: '處理器概覽，顯示晶片名稱與 CPU 規格',
  overview: '裝置總覽，顯示裝置資訊與溫度摘要',
};

if (screen && switcher) {
  switcher.hidden = false;
  switcher.addEventListener('click', (event) => {
    const button = event.target.closest('button[data-screen]');
    if (!button || button.getAttribute('aria-pressed') === 'true') return;
    const key = button.dataset.screen;
    switcher.querySelector('[aria-pressed="true"]').setAttribute('aria-pressed', 'false');
    button.setAttribute('aria-pressed', 'true');
    screen.src = `assets/${key}.png`;
    screen.alt = previews[key];
  });
}
