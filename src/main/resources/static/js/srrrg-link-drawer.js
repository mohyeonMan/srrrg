window.SrrrgLinkDrawer = (() => {
	let overlay = null;
	let panel = null;
	let lastFocus = null;

	function ensureNodes() {
		if (panel) return;
		overlay = document.createElement('div');
		overlay.className = 'drawer-overlay';
		overlay.hidden = true;
		overlay.addEventListener('click', close);

		panel = document.createElement('div');
		panel.className = 'drawer-panel';
		panel.setAttribute('role', 'dialog');
		panel.setAttribute('aria-modal', 'false');
		panel.setAttribute('aria-label', '링크 상세');
		panel.tabIndex = -1;
		panel.hidden = true;

		document.body.append(overlay, panel);
		document.addEventListener('keydown', (event) => {
			if (event.key === 'Escape' && !panel.hidden) close();
		});
	}

	function setBackgroundInert(isInert) {
		Array.from(document.body.children).forEach((node) => {
			if (node === overlay || node === panel) return;
			if (isInert) node.setAttribute('inert', ''); else node.removeAttribute('inert');
		});
	}

	function close() {
		if (!panel || panel.hidden) return;
		overlay.hidden = true;
		panel.hidden = true;
		setBackgroundInert(false);
		document.body.style.overflow = '';
		if (lastFocus) lastFocus.focus();
	}

	function el(tag, className, text) {
		const node = document.createElement(tag);
		if (className) node.className = className;
		if (text !== undefined) node.textContent = text;
		return node;
	}

	function formatDate(value) {
		return value ? new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '-';
	}

	function localDateTime(date) {
		const offset = date.getTimezoneOffset() * 60_000;
		return new Date(date.getTime() - offset).toISOString().slice(0, 16);
	}

	function expiresAtFromOption(option, customInput) {
		if (option === 'none') return null;
		if (option === 'custom') return customInput.value ? new Date(customInput.value).toISOString() : undefined;
		const [amount, unit] = option.split(':');
		const value = new Date();
		const count = Number(amount);
		if (unit === 'hours') value.setHours(value.getHours() + count);
		if (unit === 'days') value.setDate(value.getDate() + count);
		if (unit === 'months') value.setMonth(value.getMonth() + count);
		if (unit === 'years') value.setFullYear(value.getFullYear() + count);
		return value.toISOString();
	}

	/**
	 * @param {object} opts
	 * @param {string} opts.base - context path
	 * @param {(url: string, options?: object) => Promise<Response>} opts.request - authenticated fetch (401 → refresh/login)
	 * @param {(response: Response) => Promise<any>} opts.body - parse response JSON safely
	 * @param {number|string} opts.projectId
	 * @param {string} opts.code
	 * @param {string} opts.manageUrl - link to the full management console (stats + settings)
	 * @param {() => void} [opts.onChange] - called after a successful save/delete so the caller can refresh its list
	 */
	async function open(opts) {
		ensureNodes();
		lastFocus = document.activeElement;
		panel.replaceChildren(el('p', 'form-message', '링크 정보를 불러오는 중입니다.'));
		overlay.hidden = false;
		panel.hidden = false;
		setBackgroundInert(true);
		document.body.style.overflow = 'hidden';
		panel.focus();

		const response = await opts.request(`${opts.base}/api/web/projects/${opts.projectId}/links/${encodeURIComponent(opts.code)}`);
		if (!response.ok) {
			panel.replaceChildren(el('p', 'form-message error', (await opts.body(response)).message || '링크를 불러올 수 없습니다.'));
			return;
		}
		render(await response.json(), opts);
	}

	function render(link, opts) {
		const message = el('p', 'form-message', '');
		message.setAttribute('role', 'status');
		message.setAttribute('aria-live', 'polite');

		const header = el('div', 'drawer-header');
		const title = el('h2', '', link.code);
		title.tabIndex = -1;
		const closeButton = el('button', 'drawer-close', '닫기');
		closeButton.type = 'button';
		closeButton.addEventListener('click', close);
		header.append(title, closeButton);

		const shortUrlRow = el('div', 'copy-row');
		const shortUrlLink = el('a', 'project-link-short-url', link.shortUrl);
		shortUrlLink.href = link.shortUrl;
		shortUrlLink.target = '_blank';
		shortUrlLink.rel = 'noopener noreferrer';
		const copyButton = el('button', 'copy-button', '복사');
		copyButton.type = 'button';
		copyButton.addEventListener('click', async () => {
			try {
				await navigator.clipboard.writeText(link.shortUrl);
				copyButton.textContent = '복사됨';
				setTimeout(() => copyButton.textContent = '복사', 1500);
			} catch (_) { /* clipboard unavailable, ignore */ }
		});
		shortUrlRow.append(shortUrlLink, copyButton);

		const urlField = el('label', 'field');
		urlField.append(el('span', 'field-label', '원본 URL'));
		const urlInput = document.createElement('input');
		urlInput.className = 'field-input';
		urlInput.type = 'url';
		urlInput.maxLength = 2048;
		urlInput.value = link.originalUrl;
		urlInput.disabled = !link.editable;
		urlField.append(urlInput);

		const expireField = document.createElement('fieldset');
		expireField.className = 'expire-options';
		const legend = document.createElement('legend');
		legend.textContent = '만료 시각';
		expireField.append(legend);
		const options = [
			['none', '없음'], ['1:hours', '1시간'], ['1:days', '하루'],
			['1:months', '1개월'], ['1:years', '1년'], ['custom', '직접 입력']
		];
		let expiresOption = link.expiresAt ? 'custom' : 'none';
		const customInput = document.createElement('input');
		customInput.className = 'field-input expire-value-field';
		customInput.type = 'datetime-local';
		customInput.hidden = expiresOption !== 'custom';
		if (link.expiresAt) customInput.value = localDateTime(new Date(link.expiresAt));

		options.forEach(([value, labelText]) => {
			const optionButton = document.createElement('button');
			optionButton.type = 'button';
			optionButton.className = 'quick-expire-option';
			optionButton.textContent = labelText;
			optionButton.setAttribute('aria-pressed', String(value === expiresOption));
			optionButton.disabled = !link.editable;
			optionButton.addEventListener('click', () => {
				expiresOption = value;
				expireField.querySelectorAll('.quick-expire-option').forEach((button) => {
					button.setAttribute('aria-pressed', String(button === optionButton));
				});
				customInput.hidden = value !== 'custom';
				if (value === 'custom' && !customInput.value) {
					customInput.value = localDateTime(new Date(Date.now() + 60 * 60 * 1000));
				}
			});
			expireField.append(optionButton);
		});
		expireField.append(customInput);

		const meta = el('div', 'drawer-meta');
		meta.append(el('span', '', `생성 ${formatDate(link.createdAt)}`), el('span', '', `최근 수정 ${formatDate(link.updatedAt)}`));

		const manageLink = el('a', 'text-link', '통계 및 전체 관리 열기 →');
		manageLink.href = opts.manageUrl;

		const actions = el('div', 'drawer-actions');
		const primaryActions = el('div', 'drawer-actions-primary');

		if (link.editable) {
			const saveButton = el('button', 'primary-button', '변경 저장');
			saveButton.type = 'button';
			saveButton.addEventListener('click', async () => {
				const expiresAt = expiresAtFromOption(expiresOption, customInput);
				if (expiresAt === undefined) {
					message.textContent = '만료 시각을 입력하세요.';
					message.classList.add('error');
					return;
				}
				saveButton.disabled = true;
				message.classList.remove('error');
				message.textContent = '저장하는 중입니다...';
				const response = await opts.request(`${opts.base}/api/web/projects/${opts.projectId}/links/${encodeURIComponent(link.code)}`, {
					method: 'PATCH',
					body: JSON.stringify({ originalUrl: urlInput.value.trim(), expiresAt })
				});
				const responseBody = await opts.body(response);
				saveButton.disabled = false;
				if (!response.ok) {
					message.classList.add('error');
					message.textContent = responseBody.message || '변경사항을 저장할 수 없습니다.';
					return;
				}
				message.textContent = '변경사항을 저장했습니다.';
				opts.onChange?.();
				render(responseBody, opts);
			});

			const deleteButton = el('button', 'danger-button', '링크 삭제');
			deleteButton.type = 'button';
			deleteButton.addEventListener('click', async () => {
				if (!confirm('이 링크를 삭제할까요? 삭제 후에는 이 단축 URL로 이동할 수 없습니다.')) return;
				const response = await opts.request(`${opts.base}/api/web/projects/${opts.projectId}/links/${encodeURIComponent(link.code)}`, { method: 'DELETE' });
				if (!response.ok) {
					message.classList.add('error');
					message.textContent = (await opts.body(response)).message || '링크를 삭제할 수 없습니다.';
					return;
				}
				close();
				opts.onChange?.();
			});

			primaryActions.append(saveButton, deleteButton);
		}

		actions.append(manageLink, primaryActions);
		panel.replaceChildren(header, shortUrlRow, urlField, expireField, meta, message, actions);
		title.focus();
	}

	return { open, close };
})();
