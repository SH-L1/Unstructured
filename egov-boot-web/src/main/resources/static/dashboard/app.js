const state = { detail: null, run: null };

const el = Object.fromEntries(
  [...document.querySelectorAll("[id]")].map((node) => [node.id, node]),
);

function idempotencyKey(action) {
  return `${action}-${crypto.randomUUID()}`;
}

function csrfToken() {
  const item = document.cookie.split("; ").find((value) => value.startsWith("XSRF-TOKEN="));
  return item ? decodeURIComponent(item.split("=", 2)[1]) : null;
}

async function api(path, options = {}) {
  const method = options.method || "GET";
  const headers = { Accept: "application/json", ...(options.headers || {}) };
  if (options.body) headers["Content-Type"] = "application/json";
  const token = csrfToken();
  if (method !== "GET" && token) headers["X-XSRF-TOKEN"] = token;

  const response = await fetch(path, { ...options, method, headers, credentials: "same-origin" });
  const body = await response.json().catch(() => null);
  if (!response.ok || !body?.success) {
    throw new Error(body?.error?.message || body?.message || `${response.status} ${response.statusText}`);
  }
  return body.data;
}

function mutationHeaders(action, version) {
  const headers = { "Idempotency-Key": idempotencyKey(action) };
  if (version !== undefined && version !== null) headers["If-Match"] = `"${version}"`;
  return headers;
}

function showMessage(text, error = false) {
  el.message.textContent = text;
  el.message.classList.toggle("is-error", error);
  el.message.classList.remove("is-hidden");
  window.setTimeout(() => el.message.classList.add("is-hidden"), 6000);
}

async function checkServer() {
  try {
    const response = await fetch("/dashboard/index.html", { method: "HEAD", credentials: "same-origin" });
    if (!response.ok) throw new Error(`Server check failed: ${response.status}`);
    el.serverDot.className = "status-dot ok";
    el.serverStatus.textContent = "서버 연결됨";
  } catch {
    el.serverDot.className = "status-dot error";
    el.serverStatus.textContent = "서버 연결 실패";
  }
}

async function createComplaint() {
  if (!el.rawText.value.trim()) return showMessage("민원 원문을 입력하세요.", true);
  try {
    const complaint = await api("/api/v1/complaints", {
      method: "POST",
      headers: mutationHeaders("create"),
      body: JSON.stringify({
        sourceChannel: el.sourceChannel.value,
        rawText: el.rawText.value.trim(),
        locationText: el.locationText.value.trim() || null,
      }),
    });
    el.complaintId.value = complaint.id;
    await loadComplaint(complaint.id);
    showMessage("민원이 접수되었습니다. 분석은 아직 실행되지 않았습니다.");
  } catch (error) {
    showMessage(error.message, true);
  }
}

async function loadComplaint(id = el.complaintId.value.trim()) {
  if (!id) return showMessage("민원 UUID를 입력하세요.", true);
  try {
    state.detail = await api(`/api/v1/complaints/${id}`);
    el.complaintId.value = id;
    render();
  } catch (error) {
    showMessage(error.message, true);
  }
}

async function enqueue(kind) {
  const complaint = state.detail?.complaint;
  if (!complaint) return;
  try {
    state.run = await api(`/api/v1/complaints/${complaint.id}/${kind}-runs`, {
      method: "POST",
      headers: mutationHeaders(kind, complaint.version),
    });
    renderRun();
    await pollRun(state.run.id);
    await loadComplaint(complaint.id);
  } catch (error) {
    showMessage(error.message, true);
  }
}

async function pollRun(id) {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    state.run = await api(`/api/v1/runs/${id}`);
    renderRun();
    if (["SUCCEEDED", "FAILED", "BLOCKED"].includes(state.run.status)) return;
    await new Promise((resolve) => window.setTimeout(resolve, 700));
  }
  showMessage("작업 대기 시간이 초과되었습니다. 새로고침으로 상태를 확인하세요.", true);
}

async function confirmLocation(issueId) {
  const locationText = window.prompt("사람이 확인한 위치를 입력하세요.");
  if (!locationText?.trim()) return;
  try {
    await api(`/api/v1/issues/${issueId}/location-confirmations`, {
      method: "POST",
      headers: mutationHeaders("location", state.detail.complaint.version),
      body: JSON.stringify({ locationText: locationText.trim() }),
    });
    await loadComplaint(state.detail.complaint.id);
    showMessage("위치가 사람 확인으로 기록되었습니다.");
  } catch (error) {
    showMessage(error.message, true);
  }
}

async function decide(path, approved, action) {
  const draft = state.detail?.draft;
  if (!draft) return;
  try {
    await api(`/api/v1/drafts/${draft.id}/${path}`, {
      method: "POST",
      headers: mutationHeaders(action, draft.version),
      body: JSON.stringify({ approved, notes: el.reviewNotes.value.trim() || null }),
    });
    await loadComplaint(state.detail.complaint.id);
    showMessage(approved ? "결정이 기록되었습니다." : "거절 또는 반려가 기록되었습니다.");
  } catch (error) {
    showMessage(error.message, true);
  }
}

async function completeComplaint() {
  const complaint = state.detail?.complaint;
  if (!complaint) return;
  try {
    await api(`/api/v1/complaints/${complaint.id}/complete`, {
      method: "POST",
      headers: mutationHeaders("complete", complaint.version),
    });
    await loadComplaint(complaint.id);
    showMessage("외부 발송 없이 수동 완료 기록만 저장되었습니다.");
  } catch (error) {
    showMessage(error.message, true);
  }
}

function render() {
  const {
    complaint,
    analysis,
    issues = [],
    draft,
    draftClaims = [],
    evidence = [],
    verificationResults = [],
    aiRuns = [],
    humanReviews = [],
  } = state.detail;
  el.workspace.classList.remove("is-hidden");
  el.receiptNumber.textContent = complaint.receiptNumber;
  el.status.textContent = complaint.status;
  el.blocker.textContent = complaint.workflowBlocker || "없음";
  el.version.textContent = complaint.version;
  el.location.textContent = complaint.locationText || "미확정";
  el.redactedText.textContent = complaint.redactedText;
  el.analysis.classList.toggle("empty", !analysis);
  el.analysis.textContent = analysis
    ? `${analysis.complaintType} · ${analysis.urgency} · 담당 후보 ${analysis.departmentCode} · 감정 정보 ${analysis.sentiment} (참고 전용)`
    : "분석 결과가 없습니다.";

  el.issues.innerHTML = issues.map((issue) => `
    <article class="issue">
      <div><strong>이슈 ${issue.issueIndex + 1}: ${escapeHtml(issue.summary)}</strong><span>${issue.status}</span></div>
      <p>유형 ${issue.complaintType} · 관할 ${issue.jurisdictionStatus} · 안전 ${issue.safetyRisk} · 처리 가능성 ${issue.processability}</p>
      <p>담당 후보 ${escapeHtml((issue.departmentCandidates || []).join(", ")) || "없음"} · 위치 후보 ${escapeHtml((issue.locationCandidates || []).join(", ")) || "없음"}</p>
      <button class="secondary" data-location-id="${issue.id}" type="button">위치 사람 확인</button>
    </article>
  `).join("") || '<div class="empty">분석 후 복합 이슈가 표시됩니다.</div>';

  el.evidence.classList.toggle("empty", evidence.length === 0);
  el.evidence.innerHTML = evidence.map((item) => `
    <article>
      <strong>${escapeHtml(item.title)}</strong>
      <dl>
        <dt>주장 관계</dt><dd>${item.supportsClaim ? "지지 가능" : "반대 또는 검토 필요"}</dd>
        <dt>검증</dt><dd>${escapeHtml(item.sourceStatus)}</dd>
        <dt>목적</dt><dd>${escapeHtml(item.sourceType)}</dd>
        <dt>법적 근거</dt><dd>${escapeHtml(item.legalBasis || "미지정")}</dd>
        <dt>출처 버전</dt><dd>${escapeHtml(item.sourceVersion || "미지정")}</dd>
        <dt>관할</dt><dd>${escapeHtml(item.jurisdictionCode || "미지정")}</dd>
        <dt>시행</dt><dd>${escapeHtml(item.effectiveFrom || "미지정")} ~ ${escapeHtml(item.effectiveTo || "현재")}</dd>
        <dt>해시</dt><dd>${escapeHtml(item.contentHash)}</dd>
      </dl>
      <p>${escapeHtml(item.content)}</p>
      ${safeHttpUrl(item.sourceUrl) ? `<a href="${escapeHtml(item.sourceUrl)}" target="_blank" rel="noreferrer">원 출처</a>` : ""}
    </article>
  `).join("") || "연결된 근거가 없습니다.";

  el.draftStatus.textContent = draft ? `${draft.status} · v${draft.version}` : "초안 없음";
  el.draftText.textContent = draft?.draftText || "검증된 공식 근거가 있어야 초안이 생성됩니다.";
  el.draftClaims.classList.toggle("empty", draftClaims.length === 0);
  el.draftClaims.innerHTML = draftClaims.map((claim) => `
    <article class="audit-item">
      <strong>주장 ${claim.claimIndex + 1} · ${escapeHtml(claim.claimType)}</strong>
      <p>${escapeHtml(claim.claimText)}</p>
      <p>근거 ID ${escapeHtml((claim.evidenceSourceIds || []).join(", "))}</p>
    </article>
  `).join("") || "구조화 주장이 없습니다.";
  el.verificationResults.classList.toggle("empty", verificationResults.length === 0);
  el.verificationResults.innerHTML = verificationResults.map((item) => `
    <article class="${item.hardFailure ? "audit-item hard-failure" : "audit-item"}">
      <strong>${escapeHtml(item.ruleCode)} · ${escapeHtml(item.status)}</strong>
      <p>${escapeHtml(item.message)}</p>
    </article>
  `).join("") || "검증 결과가 없습니다.";
  el.aiRuns.classList.toggle("empty", aiRuns.length === 0);
  el.aiRuns.innerHTML = aiRuns.map((item) => `
    <article class="audit-item">
      <strong>${escapeHtml(item.taskType)} · ${escapeHtml(item.status)}</strong>
      <p>${escapeHtml(item.provider)} / ${escapeHtml(item.modelName)}</p>
      <p>prompt ${escapeHtml(item.promptVersion)} · schema ${escapeHtml(item.schemaVersion)}</p>
      <p>입력 해시 ${escapeHtml(item.inputHash)} · 출력 해시 ${escapeHtml(item.outputHash || "없음")}</p>
      <p>비용 상한 단위 ${item.costUnits} · ${item.durationMs}ms · 재시도 ${item.retryCount}</p>
      ${item.failureReason ? `<p class="failure">${escapeHtml(item.failureReason)}</p>` : ""}
    </article>
  `).join("") || "AI 실행 기록이 없습니다.";
  el.humanReviews.classList.toggle("empty", humanReviews.length === 0);
  el.humanReviews.innerHTML = humanReviews.map((item) => `
    <article class="audit-item">
      <strong>${escapeHtml(item.action)} · ${escapeHtml(item.actorRole)}</strong>
      <p>${escapeHtml(item.actor)}${item.notes ? ` · ${escapeHtml(item.notes)}` : ""}</p>
    </article>
  `).join("") || "검토·승인 이력이 없습니다.";
  document.querySelectorAll("[data-location-id]").forEach((button) => {
    button.addEventListener("click", () => confirmLocation(button.dataset.locationId));
  });
}

function renderRun() {
  if (!state.run) return;
  el.runStatus.classList.remove("empty");
  el.runStatus.innerHTML = `
    <strong>${state.run.jobType} · ${state.run.status}</strong>
    <p>시도 ${state.run.attempts}/${state.run.maxAttempts}</p>
    ${state.run.failureReason ? `<p class="failure">${escapeHtml(state.run.failureReason)}</p>` : ""}
  `;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function safeHttpUrl(value) {
  try {
    const url = new URL(value);
    return ["http:", "https:"].includes(url.protocol);
  } catch {
    return false;
  }
}

el.createButton.addEventListener("click", createComplaint);
el.loadButton.addEventListener("click", () => loadComplaint());
el.refreshButton.addEventListener("click", () => loadComplaint());
el.analysisButton.addEventListener("click", () => enqueue("analysis"));
el.draftButton.addEventListener("click", () => enqueue("draft"));
el.reviewApproveButton.addEventListener("click", () => decide("reviews", true, "review-pass"));
el.reviewRejectButton.addEventListener("click", () => decide("reviews", false, "review-reject"));
el.approveButton.addEventListener("click", () => decide("approvals", true, "approve"));
el.rejectButton.addEventListener("click", () => decide("approvals", false, "approval-reject"));
el.completeButton.addEventListener("click", completeComplaint);

checkServer();
