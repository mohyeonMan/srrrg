(function () {
	const app = document.getElementById('home-app');
	if (!app) {
		return;
	}

	const createLinkUrl = app.dataset.apiUrl || '/api/links';
	const manageUrl = app.dataset.manageUrl || '/manage';
	const noExpirationDisplayValue = '-';
	const state = {
		selectedExpiresAtOption: 'none',
		secretKeyCopied: false,
		discardConfirmationPending: false,
		copyTimers: new WeakMap()
	};

	const createStage = byId('create-stage');
	const createFormPanel = byId('create-form-panel');
	const form = byId('shortener-form');
	const originalUrlInput = byId('original-url');
	const expiresAtInput = byId('expires-at');
	const expirationDisclosure = byId('toggle-expiration-options');
	const expirationSettings = byId('expiration-settings');
	// 만료 시각 읽기 전용 표시. '없음' 이면 알릴 시각이 없어 행을 감춘다.
	const expiresField = byId('expires-field');
	const setExpiresFieldVisible = (option) => { if (expiresField) expiresField.hidden = option === 'none'; };
	const expirationDisclosureValue = byId('expiration-disclosure-value');
	const quickExpireOptions = form.querySelectorAll('.quick-expire-option');
	const submitButton = byId('shortener-button');
	const createUrlError = byId('create-url-error');
	const createExpirationError = byId('create-expiration-error');
	const createFormMessage = byId('create-form-message');
	const resultPanel = byId('result-panel');
	const resultTitle = byId('result-title');
	const shortUrlLink = byId('short-url');
	const secretKey = byId('secret-key');
	const secretCopyStatus = byId('secret-copy-status');
	const secretGuidance = byId('secret-guidance');
	const secretGuidanceTitle = byId('secret-guidance-title');
	const secretGuidanceDetail = byId('secret-guidance-detail');
	const createAnotherLinkButton = byId('create-another-link');
	const manageCreatedLink = byId('manage-created-link');
	const copyShortUrlButton = byId('copy-short-url');
	const copySecretKeyButton = byId('copy-secret-key');

	initCreateForm();

	function initCreateForm() {
		quickExpireOptions.forEach((option) => {
			option.addEventListener('click', () => updateExpiresAtOption(option.dataset.expiresOption));
		});

		originalUrlInput.addEventListener('input', () => {
			clearFieldError(originalUrlInput, createUrlError);
			clearCreateMessageForEdit();
			updateCreateButtonState();
		});

		expiresAtInput.addEventListener('input', () => {
			selectCustomExpiresAt();
			clearCreateMessageForEdit();
			validateCustomExpiration();
			updateCreateButtonState();
		});
		expiresAtInput.addEventListener('change', () => {
			selectCustomExpiresAt();
			clearCreateMessageForEdit();
			validateCustomExpiration();
			updateCreateButtonState();
		});

		form.addEventListener('submit', handleCreateSubmit);
		expirationDisclosure.addEventListener('click', toggleExpirationSettings);
		copyShortUrlButton.addEventListener('click', () => copyToClipboard(shortUrlLink.textContent, copyShortUrlButton));
		copySecretKeyButton.addEventListener('click', handleSecretKeyCopy);
		createAnotherLinkButton.addEventListener('click', handleCreateAnotherLink);
		window.addEventListener('beforeunload', protectUncopiedSecretKey);
		updateCreateButtonState();
	}

	async function handleCreateSubmit(event) {
		event.preventDefault();
		if (!validateCreateForm()) {
			return;
		}

		setCreateLoading(true);
		setCreateMessage('단축 URL을 만들고 있습니다...');

		try {
			const response = await fetch(createLinkUrl, {
				method: 'POST',
				headers: {
					'Content-Type': 'application/json',
					'Accept': 'application/json'
				},
				body: JSON.stringify({
					originalUrl: originalUrlInput.value.trim(),
					expiresAt: toInstantOrNull(getCreateExpiresAtValue())
				})
			});
			const body = await readApiBody(response);

			if (!response.ok) {
				showCreateError(body.message || '단축 URL 생성에 실패했습니다. 입력한 URL을 확인하세요.');
				return;
			}

			showCreateResult(body.shortUrl, body.secretKey);
			form.reset();
			resetExpiresAt();
			setCreateMessage('');
		} catch (error) {
			showCreateError('서버에 연결할 수 없습니다. 잠시 후 다시 시도하세요.');
		} finally {
			setCreateLoading(false);
			updateCreateButtonState();
		}
	}

	function validateCreateForm() {
		const originalUrl = originalUrlInput.value.trim();
		let valid = true;

		if (!originalUrl) {
			setFieldError(originalUrlInput, createUrlError, '단축할 URL을 입력하세요.');
			valid = false;
		} else if (originalUrl.length > 2048) {
			setFieldError(originalUrlInput, createUrlError, 'URL은 최대 2,048자까지 입력할 수 있습니다.');
			valid = false;
		} else if (!isHttpUrl(originalUrl)) {
			setFieldError(originalUrlInput, createUrlError, 'http 또는 https로 시작하는 올바른 URL을 입력하세요.');
			valid = false;
		} else {
			clearFieldError(originalUrlInput, createUrlError);
		}

		if (!validateCustomExpiration()) {
			valid = false;
		}

		return valid;
	}

	function validateCustomExpiration() {
		if (state.selectedExpiresAtOption !== 'custom') {
			clearFieldError(expiresAtInput, createExpirationError);
			return true;
		}

		if (!expiresAtInput.value || new Date(expiresAtInput.value).getTime() <= Date.now()) {
			setFieldError(expiresAtInput, createExpirationError, '만료 시각은 현재보다 이후여야 합니다.');
			return false;
		}

		clearFieldError(expiresAtInput, createExpirationError);
		return true;
	}

	function updateCreateButtonState() {
		if (submitButton.dataset.loading === 'true') {
			return;
		}
		const emptyUrl = !originalUrlInput.value.trim();
		const invalidCustomExpiration = state.selectedExpiresAtOption === 'custom'
				&& (!expiresAtInput.value || new Date(expiresAtInput.value).getTime() <= Date.now());
		submitButton.disabled = emptyUrl || invalidCustomExpiration;
	}

	function updateExpiresAtOption(option) {
		state.selectedExpiresAtOption = option;
		updateExpireOptionButtons(option);
		updateExpirationDisclosure(option);
		setExpiresFieldVisible(option);

		if (option === 'none') {
			setDateTimeInputMode(expiresAtInput, false, noExpirationDisplayValue);
			clearFieldError(expiresAtInput, createExpirationError);
			updateCreateButtonState();
			return;
		}

		if (option === 'custom') {
			setDateTimeInputMode(expiresAtInput, true, isNoExpirationDisplayValue(expiresAtInput.value) ? '' : expiresAtInput.value);
			if (!expiresAtInput.value) {
				expiresAtInput.value = toDatetimeLocal(addTime(new Date(), 1, 'hours'));
			}
			expiresAtInput.focus();
			validateCustomExpiration();
			updateCreateButtonState();
			return;
		}

		const [amount, unit] = option.split(':');
		setDateTimeInputMode(expiresAtInput, false, toDatetimeLocal(addTime(new Date(), Number(amount), unit)));
		clearFieldError(expiresAtInput, createExpirationError);
		updateCreateButtonState();
	}

	function selectCustomExpiresAt() {
		if (expiresAtInput.disabled) {
			return;
		}
		state.selectedExpiresAtOption = 'custom';
		updateExpireOptionButtons('custom');
	}

	function updateExpireOptionButtons(selectedOption) {
		quickExpireOptions.forEach((option) => {
			option.setAttribute('aria-pressed', String(option.dataset.expiresOption === selectedOption));
		});
	}

	function resetExpiresAt() {
		state.selectedExpiresAtOption = 'none';
		setExpiresFieldVisible('none');
		setDateTimeInputMode(expiresAtInput, false, noExpirationDisplayValue);
		updateExpireOptionButtons('none');
		updateExpirationDisclosure('none');
		expirationDisclosure.setAttribute('aria-expanded', 'false');
		expirationSettings.classList.remove('expanded');
		clearFieldError(expiresAtInput, createExpirationError);
	}

	function getCreateExpiresAtValue() {
		if (state.selectedExpiresAtOption === 'none') {
			return '';
		}
		return expiresAtInput.value;
	}

	function setCreateLoading(loading) {
		submitButton.dataset.loading = String(loading);
		submitButton.disabled = loading;
		submitButton.textContent = loading ? '생성 중...' : '단축 URL 만들기';
	}

	function resetCreateResult() {
		shortUrlLink.textContent = '';
		shortUrlLink.removeAttribute('href');
		secretKey.textContent = '';
		state.secretKeyCopied = false;
		state.discardConfirmationPending = false;
		secretCopyStatus.textContent = '복사 전';
		secretCopyStatus.classList.remove('copied');
		secretGuidance.classList.remove('danger', 'copied');
		secretGuidanceTitle.textContent = '이 키는 다시 확인할 수 없습니다.';
		secretGuidanceDetail.textContent = '링크 조회, 수정, 삭제에 필요합니다.';
		createAnotherLinkButton.textContent = '새 링크 만들기';
		resetCopyButton(copyShortUrlButton);
		resetCopyButton(copySecretKeyButton);
	}

	function showCreateResult(shortUrl, issuedSecretKey) {
		resetCreateResult();
		shortUrlLink.textContent = shortUrl;
		shortUrlLink.href = shortUrl;
		const code = extractLinkCode(shortUrl);
		manageCreatedLink.href = code ? `${manageUrl}?code=${encodeURIComponent(code)}` : manageUrl;
		secretKey.textContent = issuedSecretKey;
		showResultPanel();
	}

	function showCreateError(message) {
		setCreateState('form');
		setCreateMessage(message, true);
		originalUrlInput.focus();
	}

	function showResultPanel() {
		setCreateState('result');
		resultTitle.focus({preventScroll: true});
	}

	function setCreateState(nextState) {
		const showResult = nextState === 'result';
		createStage.dataset.state = showResult ? 'result' : 'form';
		createFormPanel.setAttribute('aria-hidden', String(showResult));
		resultPanel.setAttribute('aria-hidden', String(!showResult));
		createFormPanel.inert = showResult;
		resultPanel.inert = !showResult;
	}

	async function handleSecretKeyCopy() {
		const copied = await copyToClipboard(secretKey.textContent, copySecretKeyButton);
		if (!copied) {
			return;
		}

		state.secretKeyCopied = true;
		state.discardConfirmationPending = false;
		secretCopyStatus.textContent = '복사 완료';
		secretCopyStatus.classList.add('copied');
		secretGuidance.classList.remove('danger');
		secretGuidance.classList.add('copied');
		secretGuidanceTitle.textContent = 'secret key를 복사했습니다.';
		secretGuidanceDetail.textContent = '안전한 곳에 보관했는지 확인하세요.';
		createAnotherLinkButton.textContent = '새 링크 만들기';
	}

	function handleCreateAnotherLink() {
		if (!state.secretKeyCopied && !state.discardConfirmationPending) {
			state.discardConfirmationPending = true;
			secretGuidance.classList.add('danger');
			secretGuidanceTitle.textContent = 'secret key를 아직 복사하지 않았습니다.';
			secretGuidanceDetail.textContent = '새 링크를 만들면 이 키를 다시 확인할 수 없습니다.';
			createAnotherLinkButton.textContent = '복사하지 않고 새로 만들기';
			return;
		}

		resetCreateResult();
		setCreateState('form');
		originalUrlInput.focus({preventScroll: true});
	}

	function protectUncopiedSecretKey(event) {
		if (createStage.dataset.state !== 'result' || state.secretKeyCopied) {
			return;
		}

		event.preventDefault();
		event.returnValue = '';
	}

	function clearCreateMessageForEdit() {
		if (submitButton.dataset.loading !== 'true') {
			setCreateMessage('');
		}
	}

	function toggleExpirationSettings() {
		const expanded = expirationDisclosure.getAttribute('aria-expanded') === 'true';
		expirationDisclosure.setAttribute('aria-expanded', String(!expanded));
		expirationSettings.classList.toggle('expanded', !expanded);
		if (!expanded) {
			expirationSettings.querySelector('button[aria-pressed="true"]')?.focus({preventScroll: true});
		}
	}

	function updateExpirationDisclosure(option) {
		const labels = {
			none: '없음',
			'1:hours': '1시간',
			'1:days': '하루',
			'1:months': '1개월',
			'1:years': '1년',
			custom: '직접 입력'
		};
		expirationDisclosureValue.textContent = labels[option] || '없음';
	}

	function setCreateMessage(message, error = false) {
		createFormMessage.textContent = message;
		createFormMessage.classList.toggle('error', error);
	}

	async function copyToClipboard(value, button) {
		if (!value) {
			return false;
		}
		try {
			if (navigator.clipboard && window.isSecureContext) {
				await navigator.clipboard.writeText(value);
			} else {
				copyToClipboardFallback(value);
			}
			flashCopyButton(button, '복사됨');
			return true;
		} catch (error) {
			flashCopyButton(button, '실패');
			return false;
		}
	}

	function copyToClipboardFallback(value) {
		const textarea = document.createElement('textarea');
		textarea.value = value;
		textarea.setAttribute('readonly', '');
		textarea.style.position = 'fixed';
		textarea.style.top = '-999px';
		document.body.appendChild(textarea);
		textarea.select();
		let copied = false;
		try {
			copied = document.execCommand('copy');
		} finally {
			document.body.removeChild(textarea);
		}
		if (!copied) {
			throw new Error('Clipboard copy failed');
		}
	}

	function flashCopyButton(button, message) {
		const originalText = button.dataset.originalText || button.textContent;
		button.dataset.originalText = originalText;
		button.textContent = message;
		clearTimeout(state.copyTimers.get(button));
		const timer = window.setTimeout(() => resetCopyButton(button), 1500);
		state.copyTimers.set(button, timer);
	}

	function resetCopyButton(button) {
		button.textContent = button.dataset.originalText || '복사';
	}

	function extractLinkCode(value) {
		const trimmedValue = value.trim();
		if (/^[0-9A-Za-z]{6}$/.test(trimmedValue)) {
			return trimmedValue;
		}

		try {
			const url = new URL(trimmedValue);
			const segments = url.pathname.split('/').filter(Boolean);
			const code = segments.at(-1) || '';
			return /^[0-9A-Za-z]{6}$/.test(code) ? code : null;
		} catch (error) {
			return null;
		}
	}

	function setFieldError(input, errorElement, message) {
		input.setAttribute('aria-invalid', 'true');
		errorElement.textContent = message;
		errorElement.hidden = false;
	}

	function clearFieldError(input, errorElement) {
		input.removeAttribute('aria-invalid');
		errorElement.textContent = '';
		errorElement.hidden = true;
	}


	async function readApiBody(response) {
		try {
			return await response.json();
		} catch (error) {
			return {};
		}
	}

	function isHttpUrl(value) {
		try {
			const url = new URL(value);
			return url.protocol === 'http:' || url.protocol === 'https:';
		} catch (error) {
			return false;
		}
	}


	function setDateTimeInputMode(input, editable, value) {
		input.type = isNoExpirationDisplayValue(value) ? 'text' : 'datetime-local';
		input.disabled = !editable;
		input.value = value;
	}

	function addTime(date, amount, unit) {
		const result = new Date(date);
		if (unit === 'hours') {
			result.setHours(result.getHours() + amount);
		}
		if (unit === 'days') {
			result.setDate(result.getDate() + amount);
		}
		if (unit === 'months') {
			result.setMonth(result.getMonth() + amount);
		}
		if (unit === 'years') {
			result.setFullYear(result.getFullYear() + amount);
		}
		return result;
	}

	function toDatetimeLocal(date) {
		const year = date.getFullYear();
		const month = String(date.getMonth() + 1).padStart(2, '0');
		const day = String(date.getDate()).padStart(2, '0');
		const hour = String(date.getHours()).padStart(2, '0');
		const minute = String(date.getMinutes()).padStart(2, '0');
		return `${year}-${month}-${day}T${hour}:${minute}`;
	}

	function toInstantOrNull(value) {
		if (!value || isNoExpirationDisplayValue(value)) {
			return null;
		}
		return new Date(value).toISOString();
	}

	function isNoExpirationDisplayValue(value) {
		return value === noExpirationDisplayValue || value === '--' || value === '----. --. --. -- --:--';
	}

	function byId(id) {
		return document.getElementById(id);
	}
})();
