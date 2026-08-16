(() => {
	const app = document.querySelector('#project-api-keys-app');
	if (!app) return;

	const base = SrrrgCommon.base;
	const { request, body, element, replaceChildren, setMessage, submitting, confirmAction } = SrrrgCommon;
	let projectId = new URLSearchParams(location.search).get('projectId');
	const byId = (id) => document.getElementById(id);
	const listMessage = byId('api-keys-message');
	const createMessage = byId('create-api-key-message');
	const resultMessage = byId('created-api-key-message');
	const dialog = byId('create-api-key-dialog');
	const form = byId('create-api-key-form');
	const result = byId('created-api-key-result');
	const expiresAtInput = byId('api-key-expires-at');
	const noExpirationInput = byId('api-key-no-expiration');
	const scopeOrder = ['links:read', 'links:write', 'campaigns:read', 'campaigns:write', 'stats:read'];

	function formatDate(value) {
		if (!value) return '없음';
		return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
	}

	function recentUse(value) {
		if (!value) return '사용 기록 없음';
		const elapsed = Date.now() - new Date(value).getTime();
		const minute = 60_000;
		if (elapsed < minute) return '방금 사용';
		const units = elapsed < 60 * minute
			? [Math.floor(elapsed / minute), 'minute']
			: elapsed < 24 * 60 * minute
				? [Math.floor(elapsed / (60 * minute)), 'hour']
				: [Math.floor(elapsed / (24 * 60 * minute)), 'day'];
		if (units[0] > 30) return `${formatDate(value)} 사용`;
		return `${new Intl.RelativeTimeFormat('ko-KR', { numeric: 'auto' }).format(-units[0], units[1])} 사용`;
	}

	function keyStatus(key) {
		if (key.revokedAt) return { label: '폐기됨', className: 'danger', active: false };
		if (key.expiresAt && new Date(key.expiresAt).getTime() <= Date.now()) {
			return { label: '만료됨', className: 'warning', active: false };
		}
		return { label: '사용 가능', className: 'success', active: true };
	}

	function expirationSummary(value) {
		if (!value) return '만료 없음';
		if (new Date(value).getTime() <= Date.now()) return `${formatDate(value)} 만료`;
		return `${new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium' }).format(new Date(value))} 만료`;
	}

	function renderKey(key) {
		const status = keyStatus(key);
		const item = element('details', 'api-key-item');
		// 네이티브 exclusive accordion. 목록이 길어져도 상세 여러 개가 동시에 높이를 먹지 않는다.
		item.setAttribute('name', 'api-key-detail');

		const summary = element('summary', 'api-key-summary');
		const identity = element('span', 'api-key-summary-identity');
		identity.append(
			element('strong', '', key.name),
			element('code', '', `srrrg_pk_${key.keyPrefix}_…`),
			element('small', '', `권한 ${key.scopes.length}개 · ${recentUse(key.lastUsedAt)} · ${expirationSummary(key.expiresAt)}`)
		);
		summary.append(identity, element('span', `status-badge ${status.className}`, status.label));

		const detail = element('div', 'api-key-detail');
		const scopes = element('div', 'api-key-scope-list');
		scopes.setAttribute('aria-label', '권한');
		[...key.scopes]
			.sort((left, right) => scopeOrder.indexOf(left) - scopeOrder.indexOf(right))
			.forEach((scope) => scopes.append(element('code', 'status-badge', scope)));

		const metadata = element('dl', 'api-key-metadata');
		[
			['생성', formatDate(key.createdAt)],
			['최근 사용', key.lastUsedAt ? formatDate(key.lastUsedAt) : '사용 기록 없음'],
			['만료', key.expiresAt ? formatDate(key.expiresAt) : '없음']
		].forEach(([label, value]) => {
			const entry = element('div');
			entry.append(element('dt', '', label), element('dd', '', value));
			metadata.append(entry);
		});
		detail.append(scopes, metadata);

		if (status.active) {
			const actions = element('div', 'api-key-actions');
			const revoke = element('button', 'danger-button compact-action', '폐기');
			revoke.type = 'button';
			revoke.setAttribute('aria-label', `${key.name} API 키 폐기`);
			revoke.addEventListener('click', () => revokeKey(key));
			actions.append(revoke);
			detail.append(actions);
		}

		item.append(summary, detail);
		return item;
	}

	function renderKeys(keys) {
		const active = keys.filter((key) => keyStatus(key).active);
		const history = keys.filter((key) => !keyStatus(key).active);
		replaceChildren(byId('api-key-active-list'), active.map(renderKey));
		byId('api-key-empty').hidden = active.length > 0;
		byId('api-key-history').hidden = history.length === 0;
		byId('api-key-history-count').textContent = String(history.length);
		replaceChildren(byId('api-key-history-list'), history.map(renderKey));
	}

	async function loadKeys() {
		if (!projectId) return setMessage(listMessage, '프로젝트를 먼저 선택하세요.', true);
		byId('api-key-scroll').setAttribute('aria-busy', 'true');
		setMessage(listMessage, 'API 키를 불러오는 중입니다.');
		const response = await request(`${base}/api/web/projects/${projectId}/api-keys`);
		if (!response.ok) {
			byId('api-key-scroll').setAttribute('aria-busy', 'false');
			return setMessage(listMessage, (await body(response)).message || 'API 키를 불러올 수 없습니다.', true);
		}
		renderKeys(await response.json());
		byId('api-key-scroll').setAttribute('aria-busy', 'false');
		setMessage(listMessage, '');
	}

	function defaultExpiration() {
		const value = new Date();
		value.setDate(value.getDate() + 90);
		return new Date(value.getTime() - value.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
	}

	function clearCreatedKey() {
		byId('created-api-key').textContent = '';
		byId('created-api-key-name').textContent = '';
		setMessage(resultMessage, '');
		result.hidden = true;
		form.hidden = false;
	}

	function closeDialog() {
		if (dialog.open) dialog.close();
		clearCreatedKey();
		byId('open-create-api-key').focus();
	}

	function openDialog() {
		clearCreatedKey();
		form.reset();
		noExpirationInput.checked = false;
		expiresAtInput.disabled = false;
		expiresAtInput.required = true;
		expiresAtInput.value = defaultExpiration();
		setMessage(createMessage, '');
		dialog.showModal();
		byId('api-key-name').focus();
	}

	byId('open-create-api-key').addEventListener('click', openDialog);
	byId('close-create-api-key').addEventListener('click', closeDialog);
	byId('cancel-create-api-key').addEventListener('click', closeDialog);
	byId('finish-create-api-key').addEventListener('click', closeDialog);
	dialog.addEventListener('close', clearCreatedKey);

	noExpirationInput.addEventListener('change', () => {
		expiresAtInput.disabled = noExpirationInput.checked;
		expiresAtInput.required = !noExpirationInput.checked;
		if (!noExpirationInput.checked && !expiresAtInput.value) expiresAtInput.value = defaultExpiration();
	});

	form.addEventListener('submit', async (event) => {
		event.preventDefault();
		const data = new FormData(form);
		const name = data.get('name')?.trim();
		const scopes = data.getAll('scope');
		if (!name) return setMessage(createMessage, 'API 키 이름을 입력하세요.', true);
		if (!scopes.length) return setMessage(createMessage, '권한을 하나 이상 선택하세요.', true);
		let expiresAt = null;
		if (!noExpirationInput.checked) {
			const timestamp = new Date(expiresAtInput.value);
			if (!expiresAtInput.value || timestamp.getTime() <= Date.now()) {
				return setMessage(createMessage, '만료 시각은 현재보다 이후여야 합니다.', true);
			}
			expiresAt = timestamp.toISOString();
		}

		await submitting(event.submitter, '발급 중...', async () => {
			setMessage(createMessage, 'API 키를 발급하고 있습니다.');
			const response = await request(`${base}/api/web/projects/${projectId}/api-keys`, {
				method: 'POST', body: JSON.stringify({ name, scopes, expiresAt })
			});
			const responseBody = await body(response);
			if (!response.ok) return setMessage(createMessage, responseBody.message || 'API 키를 발급할 수 없습니다.', true);
			form.hidden = true;
			result.hidden = false;
			byId('created-api-key-name').textContent = responseBody.name || name;
			byId('created-api-key').textContent = responseBody.key;
			byId('created-api-key-title').focus();
			loadKeys();
		});
	});

	byId('copy-created-api-key').addEventListener('click', async (event) => {
		const value = byId('created-api-key').textContent;
		if (!value) return;
		try {
			await navigator.clipboard.writeText(value);
			const button = event.currentTarget;
			button.textContent = '복사됨';
			setMessage(resultMessage, 'API 키를 복사했습니다.');
			setTimeout(() => button.textContent = '복사', 1500);
		} catch (_) {
			setMessage(resultMessage, '클립보드에 복사할 수 없습니다.', true);
		}
	});

	async function revokeKey(key) {
		if (!(await confirmAction({
			title: `“${key.name}” API 키를 폐기할까요?`,
			body: `srrrg_pk_${key.keyPrefix}_…를 사용하는 연동이 즉시 중단됩니다. 폐기는 되돌릴 수 없습니다.`,
			confirmLabel: 'API 키 폐기'
		}))) return;
		const response = await request(`${base}/api/web/projects/${projectId}/api-keys/${key.id}`, { method: 'DELETE' });
		if (!response.ok) return setMessage(listMessage, (await body(response)).message || 'API 키를 폐기할 수 없습니다.', true);
		await loadKeys();
		setMessage(listMessage, 'API 키를 폐기했습니다.');
	}

	// API 탭을 실제로 연 OWNER만 목록을 조회한다.
	window.SrrrgProjectApiKeys = {
		reload(newProjectId) {
			if (newProjectId) projectId = newProjectId;
			return loadKeys();
		}
	};
})();
