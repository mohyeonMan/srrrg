(() => {
	const entries = Array.from(new URLSearchParams(window.location.search).entries());
	const rows = document.getElementById('query-parameter-rows');
	const empty = document.getElementById('query-parameter-empty');
	const tableWrap = document.getElementById('query-parameter-table-wrap');

	document.getElementById('request-url').textContent = window.location.href;
	document.getElementById('query-parameter-count').textContent = `${entries.length}개`;

	for (const [name, value] of entries) {
		const row = document.createElement('tr');
		const nameCell = document.createElement('td');
		const valueCell = document.createElement('td');

		nameCell.textContent = name;
		valueCell.textContent = value;
		row.append(nameCell, valueCell);
		rows.append(row);
	}

	if (entries.length > 0) {
		empty.hidden = true;
		tableWrap.hidden = false;
	}
})();
