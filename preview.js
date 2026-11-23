const screen = document.querySelector('#screen');
const switcher = document.querySelector('.switcher');
const previews = {
  charging: '電池畫面，顯示目前淨功率與充電檢測紀錄',
  hardware: '處理器概覽，顯示晶片名稱、架構與 CPU 製程',
  overview: 'Galaxy S26 Ultra 的裝置總覽，顯示電池、記憶體、儲存空間及 CPU 頻率',
};

if (screen && switcher) {
  switcher.hidden = false;
  switcher.addEventListener('click', (event) => {
    const button = event.target.closest('button[data-screen]');
    if (!button || button.getAttribute('aria-pressed') === 'true') return;
    const key = button.dataset.screen;
    switcher.querySelector('[aria-pressed="true"]').setAttribute('aria-pressed', 'false');
    button.setAttribute('aria-pressed', 'true');
    screen.src = `assets/${key}.png?v=device`;
    screen.alt = previews[key];
  });
}
