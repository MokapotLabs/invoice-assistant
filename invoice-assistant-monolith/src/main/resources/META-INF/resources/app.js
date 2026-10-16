// Delivery endpoints return a complete status fragment for an attended conflict.
// HTMX normally discards 409 bodies, hiding the explanation from the user.
document.addEventListener("htmx:beforeSwap", (event) => {
  const detail = event.detail;
  if (detail.xhr.status === 409 && detail.target?.id === "delivery-status"
      && detail.xhr.responseText.trimStart().startsWith('<section id="delivery-status"')) {
    detail.shouldSwap = true;
    detail.isError = false;
  }
});

document.addEventListener("DOMContentLoaded", () => {
  const preference = document.querySelector("[data-approval-preference]");
  let preferenceSave = Promise.resolve(true);
  if (preference) {
    let saved = preference.checked;
    const status = document.querySelector("[data-approval-status]");
    preference.addEventListener("change", () => {
      const desired = preference.checked;
      preference.disabled = true;
      status.textContent = "Saving approval preference…";
      preferenceSave = (async () => {
        try {
          await fetch("/", { credentials: "same-origin" });
          const response = await fetch("/preferences/auto-approval", {
            method: "POST", credentials: "same-origin",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: new URLSearchParams({ enabled: String(desired) })
          });
          if (!response.ok) throw new Error("Preference save failed");
          saved = desired;
          status.classList.add("visually-hidden");
          status.setAttribute("role", "status");
          status.textContent = "Approval preference saved.";
          return true;
        } catch (error) {
          preference.checked = saved;
          status.classList.remove("visually-hidden");
          status.setAttribute("role", "alert");
          status.textContent = "Couldn't save approval preference. Please try again.";
          return false;
        } finally {
          preference.disabled = false;
        }
      })();
    });
  }
  document.querySelectorAll("input[type='file'][data-submit-on-select]").forEach((fileInput) => {
    const form = fileInput.form;
    const label = form.querySelector("#file-name");
    const message = document.querySelector("#upload-message");
    fileInput.addEventListener("change", () => {
      const files = Array.from(fileInput.files);
      if (label) label.textContent = files.map((file) => file.name).join(", ");
      if (files.length) form.requestSubmit();
    });
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      const files = Array.from(fileInput.files);
      const total = files.reduce((sum, file) => sum + file.size, 0);
      if (!files.length || files.length > Number(form.dataset.maxFiles)
          || files.some((file) => file.size > Number(form.dataset.maxBytes))
          || total > Number(form.dataset.maxBatchBytes)) {
        message.textContent = "The selected PDFs exceed the upload limits shown above.";
        return;
      }
      fileInput.disabled = true;
      if (preference) preference.disabled = true;
      const admitted = [];
      const errors = [];
      try {
        if (!await preferenceSave) {
          message.textContent = "Save the approval preference before uploading.";
          return;
        }
        if (preference) preference.disabled = true;
        // Establish the attended local session, including when this page was opened directly.
        await fetch("/", { credentials: "same-origin" });
        for (const [index, file] of files.entries()) {
          message.textContent = `Uploading PDF ${index + 1} of ${files.length}…`;
          const body = new FormData();
          body.append("file", file);
          const response = await fetch("/tpf/payloads/invoice-file/upload", {
            method: "POST", credentials: "same-origin", body,
            headers: { "X-Tenant-Id": "local", "X-Scope-Id": "invoices" }
          });
          if (!response.ok) {
            errors.push(`${file.name}: upload failed (${response.status}).`);
            continue;
          }
          const payload = await response.json();
          admitted.push({ invoice: payload.invoice, originalFilename: file.name });
        }
        if (!admitted.length) {
          message.textContent = errors.join(" ");
          return;
        }
        message.textContent = preference?.checked
          ? "Checking invoices… Confident invoices proceed automatically; uncertain invoices await review."
          : "Checking invoices… Invoices will wait for your review.";
        const response = await fetch("/invoices", {
          method: "POST", credentials: "same-origin",
          headers: { "Content-Type": "application/json", "HX-Request": "true" },
          body: JSON.stringify(admitted)
        });
        const redirect = response.headers.get("HX-Redirect");
        if (redirect && !errors.length) {
          window.location.assign(redirect);
          return;
        }
        if (redirect) {
          const link = document.createElement("a");
          link.href = redirect;
          link.textContent = "View the uploaded invoice";
          message.textContent = errors.join(" ");
          message.append(" ", link);
          return;
        }
        const html = await response.text();
        const page = new DOMParser().parseFromString(html, "text/html");
        if (page.querySelector("main")) {
          document.querySelector("main").replaceWith(page.querySelector("main"));
          if (errors.length) {
            const notice = document.createElement("p");
            notice.textContent = errors.join(" ");
            notice.setAttribute("role", "alert");
            document.querySelector("main").prepend(notice);
          }
          window.htmx?.process(document.querySelector("main"));
        } else {
          message.innerHTML = html;
        }
      } catch (error) {
        message.textContent = "The upload could not finish. Check the connection and try again; already admitted PDFs are detected as duplicates.";
      } finally {
        fileInput.disabled = false;
        fileInput.value = "";
        if (preference) preference.disabled = false;
      }
    });
  });
});
