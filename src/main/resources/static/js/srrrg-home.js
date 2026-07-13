(function () {
	const app = document.getElementById('home-app');
	if (!app) {
		return;
	}

	const createLinkUrl = app.dataset.apiUrl || '/api/links';
	const noExpirationDisplayValue = '-';
	const state = {
		selectedExpiresAtOption: 'none',
		selectedManagementExpiresAtOption: 'none',
		managedCode: null,
		managedSecretKey: null,
		managedOriginalUrl: null,
		managedExpiresAtValue: null,
		lastFocusedElement: null,
		copyTimers: new WeakMap()
	};

	const form = byId('shortener-form');
	const originalUrlInput = byId('original-url');
	const expiresAtInput = byId('expires-at');
	const quickExpireOptions = form.querySelectorAll('.quick-expire-option');
	const submitButton = byId('shortener-button');
	const createUrlError = byId('create-url-error');
	const createExpirationError = byId('create-expiration-error');
	const createFormMessage = byId('create-form-message');
	const resultPanel = byId('result-panel');
	const resultStatusBadge = byId('result-status-badge');
	const resultTitle = byId('result-title');
	const resultLabel = byId('result-label');
	const shortUrlLink = byId('short-url');
	const secretKeySection = byId('secret-key-section');
	const secretKey = byId('secret-key');
	const copyShortUrlButton = byId('copy-short-url');
	const copySecretKeyButton = byId('copy-secret-key');

	const managementLookupForm = byId('management-lookup-form');
	const managementCodeInput = byId('lookup-link-ref');
	const managementSecretKeyInput = byId('lookup-access-token');
	const managementLookupButton = byId('management-lookup-button');
	const managementMessage = byId('management-message');
	const managementModal = byId('management-modal');
	const managementModalCloseButton = byId('management-modal-close-button');
	const managementStatusBadge = byId('management-status-badge');
	const managementModalMessage = byId('management-modal-message');
	const managementShortUrl = byId('management-short-url');
	const copyManagementShortUrlButton = byId('copy-management-short-url');
	const managementExpirationStatus = byId('management-expiration-status');
	const managementVerificationStatus = byId('management-verification-status');
	const managementVerifiedAt = byId('management-verified-at');
	const managementCreatedAt = byId('management-created-at');
	const managementUpdatedAt = byId('management-updated-at');
	const managementEditForm = byId('management-edit-form');
	const managementOriginalUrlInput = byId('management-original-url');
	const managementOriginalUrlResetButton = byId('management-original-url-reset-button');
	const managementExpiresAtInput = byId('management-expires-at');
	const managementExpiresAtResetButton = byId('management-expires-at-reset-button');
	const managementQuickExpireOptions = managementEditForm.querySelectorAll('[data-management-expires-option]');
	const managementClickCount = byId('management-click-count');
	const managementRedirectCount = byId('management-redirect-count');
	const managementSaveButton = byId('management-save-button');
	const managementDeleteButton = byId('management-delete-button');

	initCreateForm();
	initManagement();

	function initCreateForm() {
		quickExpireOptions.forEach((option) => {
			option.addEventListener('click', () => updateExpiresAtOption(option.dataset.expiresOption));
		});

		originalUrlInput.addEventListener('input', () => {
			clearFieldError(originalUrlInput, createUrlError);
			updateCreateButtonState();
		});

		expiresAtInput.addEventListener('input', () => {
			selectCustomExpiresAt();
			validateCustomExpiration();
			updateCreateButtonState();
		});
		expiresAtInput.addEventListener('change', () => {
			selectCustomExpiresAt();
			validateCustomExpiration();
			updateCreateButtonState();
		});

		form.addEventListener('submit', handleCreateSubmit);
		copyShortUrlButton.addEventListener('click', () => copyToClipboard(shortUrlLink.textContent, copyShortUrlButton));
		copySecretKeyButton.addEventListener('click', () => copyToClipboard(secretKey.textContent, copySecretKeyButton));
		updateCreateButtonState();
	}

	async function handleCreateSubmit(event) {
		event.preventDefault();
		if (!validateCreateForm()) {
			return;
		}

		setCreateLoading(true);
		hideResult();
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
		setDateTimeInputMode(expiresAtInput, false, noExpirationDisplayValue);
		updateExpireOptionButtons('none');
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

	function hideResult() {
		resultPanel.hidden = true;
		resultPanel.classList.remove('error');
		resultStatusBadge.className = 'status-badge success';
		resultStatusBadge.textContent = '생성 완료';
		shortUrlLink.textContent = '';
		shortUrlLink.removeAttribute('href');
		secretKey.textContent = '';
		secretKeySection.hidden = false;
		copyShortUrlButton.hidden = false;
		resetCopyButton(copyShortUrlButton);
		resetCopyButton(copySecretKeyButton);
	}

	function showCreateResult(shortUrl, issuedSecretKey) {
		resultPanel.classList.remove('error');
		resultStatusBadge.className = 'status-badge success';
		resultStatusBadge.textContent = '생성 완료';
		resultTitle.textContent = '단축 URL이 생성되었습니다';
		resultLabel.textContent = '단축 URL';
		shortUrlLink.textContent = shortUrl;
		shortUrlLink.href = shortUrl;
		secretKeySection.hidden = false;
		secretKey.textContent = issuedSecretKey;
		showResultPanel();
	}

	function showCreateError(message) {
		resultPanel.classList.add('error');
		resultStatusBadge.className = 'status-badge danger';
		resultStatusBadge.textContent = '생성 실패';
		resultTitle.textContent = '단축 URL 생성 실패';
		resultLabel.textContent = '오류';
		shortUrlLink.textContent = message;
		shortUrlLink.removeAttribute('href');
		secretKeySection.hidden = true;
		copyShortUrlButton.hidden = true;
		setCreateMessage('');
		showResultPanel();
	}

	function showResultPanel() {
		resultPanel.hidden = false;
		resultTitle.focus();
	}

	function setCreateMessage(message, error = false) {
		createFormMessage.textContent = message;
		createFormMessage.classList.toggle('error', error);
	}

	function initManagement() {
		managementLookupForm.addEventListener('submit', handleManagementLookup);
		managementEditForm.addEventListener('submit', handleManagementSave);
		managementDeleteButton.addEventListener('click', handleManagementDelete);
		managementModalCloseButton.addEventListener('click', closeManagementModal);
		managementQuickExpireOptions.forEach((option) => {
			option.addEventListener('click', () => updateManagementExpiresAtOption(option.dataset.managementExpiresOption));
		});
		managementOriginalUrlResetButton.addEventListener('click', resetManagementOriginalUrl);
		managementExpiresAtResetButton.addEventListener('click', resetManagementExpiresAt);
		managementOriginalUrlInput.addEventListener('input', () => {
			showManagementModalMessage('');
			updateManagementSaveButtonState();
		});
		managementExpiresAtInput.addEventListener('input', () => {
			selectCustomManagementExpiresAt();
			showManagementModalMessage('');
			updateManagementSaveButtonState();
		});
		managementExpiresAtInput.addEventListener('change', () => {
			selectCustomManagementExpiresAt();
			showManagementModalMessage('');
			updateManagementSaveButtonState();
		});
		copyManagementShortUrlButton.addEventListener('click', () => {
			copyToClipboard(managementShortUrl.textContent, copyManagementShortUrlButton);
		});

		managementModal.addEventListener('click', (event) => {
			if (event.target === managementModal) {
				closeManagementModal();
			}
		});

		document.addEventListener('keydown', handleDocumentKeydown);
	}

	async function handleManagementLookup(event) {
		event.preventDefault();

		const code = extractLinkCode(managementCodeInput.value);
		const secretKeyValue = managementSecretKeyInput.value.trim();
		if (!code) {
			showManagementMessage('6자리 단축 코드 또는 올바른 단축 URL을 입력하세요.', true);
			return;
		}
		if (!secretKeyValue) {
			showManagementMessage('secret key를 입력하세요.', true);
			return;
		}

		setButtonLoading(managementLookupButton, true, '조회 중...');
		showManagementMessage('');
		showManagementModalMessage('');

		try {
			const response = await fetch(`${createLinkUrl}/${encodeURIComponent(code)}`, {
				headers: {
					'Accept': 'application/json',
					'X-Srrrg-Secret-Key': secretKeyValue
				}
			});
			const body = await readApiBody(response);
			if (!response.ok) {
				showManagementMessage(body.message || '링크를 조회하지 못했습니다.', true);
				return;
			}

			state.managedCode = code;
			state.managedSecretKey = secretKeyValue;
			renderManagedLink(body);
			showManagementMessage('');
			showManagementModalMessage('링크 정보를 불러왔습니다.');
			openManagementModal();
		} catch (error) {
			showManagementMessage('서버에 연결할 수 없습니다. 잠시 후 다시 시도하세요.', true);
		} finally {
			setButtonLoading(managementLookupButton, false, '조회하기');
		}
	}

	async function handleManagementSave(event) {
		event.preventDefault();
		if (!state.managedCode || !state.managedSecretKey) {
			return;
		}
		updateManagementSaveButtonState();
		if (managementSaveButton.disabled) {
			return;
		}

		const requestBody = {};
		const originalUrl = managementOriginalUrlInput.value.trim();
		if (!isHttpUrl(originalUrl)) {
			showManagementModalMessage('http 또는 https로 시작하는 올바른 URL을 입력하세요.', true);
			return;
		}
		if (originalUrl !== state.managedOriginalUrl) {
			requestBody.originalUrl = originalUrl;
		}
		const managementExpiresAtValue = getManagementExpiresAtValue();
		const expiresAtChanged = managementExpiresAtValue !== state.managedExpiresAtValue;
		if (expiresAtChanged && managementExpiresAtValue) {
			const expiresAt = new Date(managementExpiresAtValue);
			if (expiresAt.getTime() <= Date.now()) {
				showManagementModalMessage('만료 시각은 현재보다 이후여야 합니다.', true);
				return;
			}
		}
		if (expiresAtChanged) {
			requestBody.expiresAt = toInstantOrNull(managementExpiresAtValue);
		}

		setButtonLoading(managementSaveButton, true, '저장 중...');
		managementDeleteButton.disabled = true;
		updateManagementSaveButtonState();
		showManagementModalMessage('');

		try {
			const response = await fetch(`${createLinkUrl}/${encodeURIComponent(state.managedCode)}`, {
				method: 'PATCH',
				headers: {
					'Content-Type': 'application/json',
					'Accept': 'application/json',
					'X-Srrrg-Secret-Key': state.managedSecretKey
				},
				body: JSON.stringify(requestBody)
			});
			const body = await readApiBody(response);
			if (!response.ok) {
				showManagementModalMessage(body.message || '링크를 수정하지 못했습니다.', true);
				return;
			}

			renderManagedLink(body);
			showManagementModalMessage('변경사항을 저장했습니다.');
		} catch (error) {
			showManagementModalMessage('서버에 연결할 수 없습니다. 잠시 후 다시 시도하세요.', true);
		} finally {
			setButtonLoading(managementSaveButton, false, '변경 저장');
			managementDeleteButton.disabled = false;
			updateManagementSaveButtonState();
		}
	}

	async function handleManagementDelete() {
		if (!state.managedCode || !state.managedSecretKey) {
			return;
		}
		if (!window.confirm('이 링크를 삭제할까요?\n삭제 후에는 이 단축 URL로 이동할 수 없습니다.')) {
			return;
		}

		setButtonLoading(managementDeleteButton, true, '삭제 중...');
		managementSaveButton.disabled = true;
		updateManagementSaveButtonState();
		showManagementModalMessage('');

		try {
			const response = await fetch(`${createLinkUrl}/${encodeURIComponent(state.managedCode)}`, {
				method: 'DELETE',
				headers: {
					'Accept': 'application/json',
					'X-Srrrg-Secret-Key': state.managedSecretKey
				}
			});
			const body = await readApiBody(response);
			if (!response.ok) {
				showManagementModalMessage(body.message || '링크를 삭제하지 못했습니다.', true);
				return;
			}

			closeManagementModal();
			clearManagedState();
			managementSecretKeyInput.value = '';
			showManagementMessage('링크를 삭제했습니다.');
		} catch (error) {
			showManagementModalMessage('서버에 연결할 수 없습니다. 잠시 후 다시 시도하세요.', true);
		} finally {
			setButtonLoading(managementDeleteButton, false, '링크 삭제');
			updateManagementSaveButtonState();
		}
	}

	function renderManagedLink(link) {
		managementShortUrl.textContent = link.shortUrl;
		managementShortUrl.href = link.shortUrl;
		managementClickCount.textContent = String(link.statistics.clickCount);
		managementRedirectCount.textContent = String(link.statistics.redirectCount);
		managementExpirationStatus.textContent = formatExpiration(link.expiresAt);
		// 저장된 검사 상태와 시각을 관리 화면에 그대로 표시함.
		const verification = verificationPresentation(link.status);
		managementVerificationStatus.textContent = verification.detail;
		managementVerifiedAt.textContent = link.verifiedAt ? formatDateTime(link.verifiedAt) : '-';
		managementCreatedAt.textContent = formatDateTime(link.createdAt);
		managementUpdatedAt.textContent = formatDateTime(link.updatedAt);
		managementOriginalUrlInput.value = link.originalUrl;
		renderManagedExpiration(link.expiresAt);
		state.managedOriginalUrl = managementOriginalUrlInput.value;
		updateManagementSaveButtonState();

		const expired = link.expiresAt && new Date(link.expiresAt).getTime() <= Date.now();
		managementStatusBadge.textContent = expired ? '만료됨' : verification.label;
		managementStatusBadge.className = expired ? 'status-badge warning' : `status-badge ${verification.className}`.trim();
	}

	function verificationPresentation(status) {
		// 서버 상태를 사용자 문구와 배지 색상으로 변환함.
		switch (status) {
			case 'NO_THREAT_FOUND':
				return { label: '위협 미탐지', detail: '알려진 위협 미탐지', className: 'success' };
			case 'THREAT_DETECTED':
				return { label: '위협 탐지됨', detail: '알려진 피싱·악성 사이트 위협 탐지', className: 'danger' };
			case 'CHECK_FAILED':
				return { label: '검사 실패', detail: '최근 안전 검사를 완료하지 못함', className: 'warning' };
			default:
				return { label: '검사 이력 없음', detail: '아직 안전 검사 이력이 없음', className: '' };
		}
	}

	function renderManagedExpiration(expiresAt) {
		if (expiresAt) {
			setDateTimeInputMode(managementExpiresAtInput, true, toDatetimeLocal(new Date(expiresAt)));
			state.selectedManagementExpiresAtOption = 'custom';
			state.managedExpiresAtValue = managementExpiresAtInput.value;
			updateManagementExpireOptionButtons('custom');
			return;
		}

		setDateTimeInputMode(managementExpiresAtInput, false, noExpirationDisplayValue);
		state.selectedManagementExpiresAtOption = 'none';
		state.managedExpiresAtValue = '';
		updateManagementExpireOptionButtons('none');
	}

	function updateManagementExpiresAtOption(option) {
		state.selectedManagementExpiresAtOption = option;
		updateManagementExpireOptionButtons(option);
		showManagementModalMessage('');

		if (option === 'none') {
			setDateTimeInputMode(managementExpiresAtInput, false, noExpirationDisplayValue);
			updateManagementSaveButtonState();
			return;
		}

		if (option === 'custom') {
			setDateTimeInputMode(managementExpiresAtInput, true, isNoExpirationDisplayValue(managementExpiresAtInput.value) ? '' : managementExpiresAtInput.value);
			if (!managementExpiresAtInput.value) {
				managementExpiresAtInput.value = toDatetimeLocal(addTime(new Date(), 1, 'hours'));
			}
			managementExpiresAtInput.focus();
			updateManagementSaveButtonState();
			return;
		}

		const [amount, unit] = option.split(':');
		setDateTimeInputMode(managementExpiresAtInput, false, toDatetimeLocal(addTime(new Date(), Number(amount), unit)));
		updateManagementSaveButtonState();
	}

	function selectCustomManagementExpiresAt() {
		if (managementExpiresAtInput.disabled) {
			return;
		}
		state.selectedManagementExpiresAtOption = 'custom';
		updateManagementExpireOptionButtons('custom');
	}

	function updateManagementExpireOptionButtons(selectedOption) {
		managementQuickExpireOptions.forEach((option) => {
			option.setAttribute('aria-pressed', String(option.dataset.managementExpiresOption === selectedOption));
		});
	}

	function getManagementExpiresAtValue() {
		if (state.selectedManagementExpiresAtOption === 'none') {
			return '';
		}
		return managementExpiresAtInput.value;
	}

	function resetManagementOriginalUrl() {
		if (state.managedOriginalUrl === null) {
			return;
		}
		managementOriginalUrlInput.value = state.managedOriginalUrl;
		showManagementModalMessage('');
		updateManagementSaveButtonState();
	}

	function resetManagementExpiresAt() {
		if (state.managedExpiresAtValue === null) {
			return;
		}
		if (state.managedExpiresAtValue) {
			setDateTimeInputMode(managementExpiresAtInput, true, state.managedExpiresAtValue);
			state.selectedManagementExpiresAtOption = 'custom';
			updateManagementExpireOptionButtons('custom');
		} else {
			setDateTimeInputMode(managementExpiresAtInput, false, noExpirationDisplayValue);
			state.selectedManagementExpiresAtOption = 'none';
			updateManagementExpireOptionButtons('none');
		}
		showManagementModalMessage('');
		updateManagementSaveButtonState();
	}

	function hasManagementOriginalUrlChange() {
		return state.managedOriginalUrl !== null
				&& managementOriginalUrlInput.value.trim() !== state.managedOriginalUrl;
	}

	function hasManagementExpiresAtChange() {
		return state.managedExpiresAtValue !== null
				&& getManagementExpiresAtValue() !== state.managedExpiresAtValue;
	}

	function hasManagementChanges() {
		if (!state.managedCode || !state.managedSecretKey) {
			return false;
		}
		return hasManagementOriginalUrlChange() || hasManagementExpiresAtChange();
	}

	function updateManagementSaveButtonState() {
		const loading = managementSaveButton.dataset.loading === 'true' || managementDeleteButton.dataset.loading === 'true';
		const originalUrlChanged = hasManagementOriginalUrlChange();
		const expiresAtChanged = hasManagementExpiresAtChange();
		managementSaveButton.disabled = loading || !(originalUrlChanged || expiresAtChanged);
		managementOriginalUrlResetButton.disabled = loading || !originalUrlChanged;
		managementExpiresAtResetButton.disabled = loading || !expiresAtChanged;
	}

	function openManagementModal() {
		state.lastFocusedElement = document.activeElement;
		managementModal.classList.add('visible');
		document.body.classList.add('modal-open');
		managementModalCloseButton.focus();
	}

	function closeManagementModal() {
		if (!managementModal.classList.contains('visible')) {
			return;
		}
		managementModal.classList.remove('visible');
		document.body.classList.remove('modal-open');
		if (state.lastFocusedElement) {
			state.lastFocusedElement.focus();
		}
	}

	function handleDocumentKeydown(event) {
		if (!managementModal.classList.contains('visible')) {
			return;
		}
		if (event.key === 'Escape') {
			closeManagementModal();
			return;
		}
		if (event.key === 'Tab') {
			trapFocus(event, managementModal);
		}
	}

	function trapFocus(event, container) {
		const focusable = Array.from(container.querySelectorAll(
			'a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'
		)).filter((element) => element.getClientRects().length > 0);
		if (focusable.length === 0) {
			return;
		}

		const first = focusable[0];
		const last = focusable[focusable.length - 1];
		if (event.shiftKey && document.activeElement === first) {
			event.preventDefault();
			last.focus();
			return;
		}
		if (!event.shiftKey && document.activeElement === last) {
			event.preventDefault();
			first.focus();
		}
	}

	function clearManagedState() {
		state.managedCode = null;
		state.managedSecretKey = null;
		state.managedOriginalUrl = null;
		state.managedExpiresAtValue = null;
		state.selectedManagementExpiresAtOption = 'none';
		showManagementModalMessage('');
		updateManagementSaveButtonState();
	}

	async function copyToClipboard(value, button) {
		if (!value) {
			return;
		}
		try {
			if (navigator.clipboard && window.isSecureContext) {
				await navigator.clipboard.writeText(value);
			} else {
				copyToClipboardFallback(value);
			}
			flashCopyButton(button, '복사됨');
		} catch (error) {
			flashCopyButton(button, '실패');
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
		document.execCommand('copy');
		document.body.removeChild(textarea);
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

	function showManagementMessage(message, error = false) {
		managementMessage.textContent = message;
		managementMessage.classList.toggle('error', error);
	}

	function showManagementModalMessage(message, error = false) {
		managementModalMessage.textContent = message;
		managementModalMessage.classList.toggle('error', error);
	}

	function setButtonLoading(button, loading, text) {
		button.dataset.loading = String(loading);
		button.disabled = loading;
		button.textContent = text;
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

	function formatExpiration(value) {
		if (!value) {
			return '--';
		}
		const expiration = new Date(value);
		const prefix = expiration.getTime() <= Date.now() ? '만료됨 · ' : '';
		return prefix + formatDateTime(value);
	}

	function formatDateTime(value) {
		if (!value) {
			return '-';
		}
		return new Intl.DateTimeFormat('ko-KR', {
			dateStyle: 'medium',
			timeStyle: 'short'
		}).format(new Date(value));
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
