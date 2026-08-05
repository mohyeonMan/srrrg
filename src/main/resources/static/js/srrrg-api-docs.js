(() => {
	const root = document.getElementById("api-contract");
	if (!root) return;
	const base = document.querySelector('meta[name="context-path"]')?.content.replace(/\/$/, "") || "";
	const text = (tag, value) => { const element = document.createElement(tag); element.textContent = value; return element; };
	fetch(`${base}/openapi.json`)
		.then(response => response.ok ? response.json() : Promise.reject())
		.then(spec => {
			root.replaceChildren();
			Object.entries(spec.paths ?? {}).forEach(([path, methods]) => Object.entries(methods)
				.filter(([method]) => ["get", "post", "patch", "delete"].includes(method))
				.forEach(([method, operation]) => {
					const details = document.createElement("details");
					details.append(text("summary", `${method.toUpperCase()} ${path} — ${operation.summary ?? ""}`));
					const body = text("pre", JSON.stringify({ parameters: operation.parameters, requestBody: operation.requestBody, responses: operation.responses }, null, 2));
					details.append(body); root.append(details);
				}));
			const schemas = spec.components?.schemas;
			if (schemas && Object.keys(schemas).length) {
				root.append(text("h3", "Schema"));
				root.append(text("pre", JSON.stringify(schemas, null, 2)));
			}
		})
		.catch(() => root.replaceChildren(text("p", "API 명세를 불러오지 못했습니다. OpenAPI JSON을 확인하세요.")));
})();
