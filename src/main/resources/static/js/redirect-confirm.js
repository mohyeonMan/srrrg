(function () {
	'use strict';
	const root = document.getElementById('redirect-check');
	// 동일 페이지에서 검사 로직이 중복 실행되지 않게 함.
	if (!root || root.dataset.started === 'true') return;
	root.dataset.started = 'true';

	const title = document.getElementById('check-title');
	const description = document.getElementById('check-description');
	const threatStep = document.getElementById('threat-step');
	const redirectStep = document.getElementById('redirect-step');
	const actions = document.getElementById('result-actions');
	const continueButton = document.getElementById('continue-button');
	const retryButton = document.getElementById('retry-button');
	const backButton = document.getElementById('go-back-button');
	const startedAt = Date.now();
	let terminal = false;
	let redirectUrl = null;

	backButton.addEventListener('click', () => {
		// 방문 기록이 없을 때만 srrrg 메인으로 이동함.
		if (window.history.length > 1) window.history.back();
		else window.location.replace(root.dataset.homeUrl || '/');
	});
	retryButton.addEventListener('click', () => {
		if (!retryButton.disabled) {
			retryButton.disabled = true;
			window.location.reload();
		}
	});
	continueButton.addEventListener('click', () => {
		if (!redirectUrl || continueButton.disabled) return;
		continueButton.disabled = true;
		window.location.replace(redirectUrl);
	});

	const cachedStatus = root.dataset.cachedStatus;
	// 최근 검사 결과가 있으면 검사 API를 호출하지 않고 저장 결과를 재사용함.
	if (cachedStatus === 'NO_THREAT_FOUND' || cachedStatus === 'THREAT_DETECTED' || cachedStatus === 'CHECK_FAILED') {
		terminal = true;
		description.textContent = '최근 1시간 이내 검사 결과를 확인했습니다.';
		window.setTimeout(() => showCachedResult(cachedStatus, root.dataset.cachedRedirectUrl || null), 600);
		return;
	}

	const controller = new AbortController();
	// 5초가 지나면 요청을 중단하고 늦게 도착한 응답도 무시함.
	const timeoutId = window.setTimeout(() => {
		if (terminal) return;
		terminal = true;
		controller.abort();
		showFailure(null);
	}, 5000);

	fetch(root.dataset.checkUrl, {
		method: 'POST',
		headers: { 'Accept': 'application/json' },
		cache: 'no-store',
		credentials: 'same-origin',
		signal: controller.signal
	}).then((response) => {
		if (!response.ok) throw new Error('redirect check request failed');
		return response.json();
	}).then(async (result) => {
		if (terminal) return;
		window.clearTimeout(timeoutId);
		// 검사 결과가 빨라도 중간 페이지를 최소 600ms 노출함.
		const minimumDelay = Math.max(0, 600 - (Date.now() - startedAt));
		if (minimumDelay) await delay(minimumDelay);
		if (terminal) return;
		terminal = true;
		if (result.status === 'NO_THREAT_FOUND' && typeof result.redirectUrl === 'string') {
			showSuccess(result.redirectUrl);
		} else if (result.status === 'THREAT_DETECTED') {
			showThreat();
		} else {
			showFailure(typeof result.redirectUrl === 'string' ? result.redirectUrl : null);
		}
	}).catch(() => {
		if (terminal) return;
		window.clearTimeout(timeoutId);
		terminal = true;
		window.setTimeout(() => showFailure(null), Math.max(0, 600 - (Date.now() - startedAt)));
	});

	function showSuccess(url) {
		setStep(threatStep, 'is-complete', '✓', '피싱·악성 사이트 검사', '미탐지');
		setStep(redirectStep, 'is-complete', '✓', '이동 준비', '완료');
		title.textContent = '알려진 위협이 발견되지 않았습니다.';
		description.textContent = '목적지로 이동합니다.';
		// 완료 상태를 짧게 보여준 뒤 뒤로 가기 기록을 남기지 않고 이동함.
		window.setTimeout(() => window.location.replace(url), 250);
	}

	function showCachedResult(status, url) {
		if (status === 'NO_THREAT_FOUND' && url) {
			showSuccess(url);
		} else if (status === 'THREAT_DETECTED') {
			showThreat();
		} else {
			showFailure(url);
		}
	}

	function showThreat() {
		root.classList.add('is-danger');
		setStep(threatStep, 'is-danger', '!', '피싱·악성 사이트 검사', '탐지');
		setStep(redirectStep, 'is-blocked', '×', '이동 차단', '차단');
		title.textContent = '위험한 링크로 확인되었습니다.';
		description.textContent = 'Google Safe Browsing에서 알려진 피싱 또는 악성 사이트로 탐지되어 이동을 차단했습니다.';
		actions.hidden = false;
	}

	function showFailure(url) {
		redirectUrl = url;
		root.classList.add('is-warning');
		setStep(threatStep, 'is-warning', '!', '피싱·악성 사이트 검사', '실패');
		setStep(redirectStep, 'is-pending', '○', '이동 보류', '대기');
		title.textContent = '현재 링크의 안전 여부를 확인하지 못했습니다.';
		description.textContent = '검사 서비스의 일시적인 오류일 수 있습니다. 목적지 주소를 직접 확인한 뒤 이동해 주세요.';
		// 서버가 재검증한 URL을 전달한 경우에만 수동 이동을 허용함.
		continueButton.hidden = !redirectUrl;
		retryButton.hidden = Boolean(redirectUrl);
		actions.hidden = false;
	}

	function setStep(step, className, marker, label, state) {
		step.className = 'check-step ' + className;
		step.querySelector('.step-marker').textContent = marker;
		step.querySelector('span:nth-child(2)').textContent = label;
		step.querySelector('strong').textContent = state;
	}

	function delay(milliseconds) {
		return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
	}
})();
