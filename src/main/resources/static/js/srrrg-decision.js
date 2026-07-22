(function () {
	const retryLink = document.getElementById('retry-link');
	const retryLabel = document.getElementById('retry-label');
	if (!retryLink || !retryLabel) {
		return;
	}

	let seconds = Number(retryLink.dataset.retryAfter || 0);
	const timer = window.setInterval(() => {
		seconds -= 1;
		if (seconds > 0) {
			retryLabel.textContent = `${seconds}초 후 다시 시도`;
			return;
		}

		window.clearInterval(timer);
		retryLabel.textContent = '다시 시도';
		retryLink.removeAttribute('aria-disabled');
		retryLink.removeAttribute('tabindex');
	}, 1000);

	retryLink.addEventListener('click', (event) => {
		if (retryLink.getAttribute('aria-disabled') === 'true') {
			event.preventDefault();
		}
	});
})();
