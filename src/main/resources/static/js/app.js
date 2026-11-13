/* ==========================================================================
   LibraTrack — UI behaviour (vanilla JavaScript)
   All data changes go through server-side forms; this file only improves the experience.
   ========================================================================== */
(function () {
    "use strict";

    const hasBootstrap = typeof window.bootstrap !== "undefined";

    /* ---------- Offline icon fallback ----------
       If the Bootstrap Icons stylesheet did not load, switch to the bundled icon font. */
    window.addEventListener("load", function () {
        const icon = document.querySelector(".bi");
        if (!icon) {
            return;
        }
        const content = window.getComputedStyle(icon, "::before").getPropertyValue("content");
        if (!content || content === "none" || content === "normal" || content === '""') {
            document.documentElement.classList.add("icons-offline");
        }
    });

    /* ---------- Sidebar (mobile) ---------- */
    document.querySelectorAll("[data-sidebar-toggle]").forEach(function (btn) {
        btn.addEventListener("click", function () {
            document.body.classList.toggle("sidebar-open");
        });
    });
    document.querySelectorAll("[data-sidebar-close], .sidebar-backdrop").forEach(function (el) {
        el.addEventListener("click", function () {
            document.body.classList.remove("sidebar-open");
        });
    });

    /* ---------- Toast notifications (from flash messages) ---------- */
    document.querySelectorAll(".toast[data-autoshow]").forEach(function (el) {
        if (hasBootstrap) {
            window.bootstrap.Toast.getOrCreateInstance(el).show();
        } else {
            el.classList.add("show");
        }
    });

    /* ---------- Loading state on submit ---------- */
    function setLoading(form) {
        const button = form.querySelector("button[type=submit][data-loading-text]");
        if (!button || button.disabled) {
            return;
        }
        button.dataset.originalHtml = button.innerHTML;
        button.disabled = true;
        button.innerHTML = '<span class="spinner-border spinner-inline me-2" role="status"></span>'
            + button.dataset.loadingText;
    }

    // Restore buttons when the page is shown again from the back/forward cache.
    window.addEventListener("pageshow", function () {
        document.querySelectorAll("button[data-original-html]").forEach(function (button) {
            button.disabled = false;
            button.innerHTML = button.dataset.originalHtml;
        });
    });

    /* ---------- Confirmation dialog ----------
       <form data-confirm="Message" data-confirm-title="Delete book?" data-confirm-button="Delete"> */
    const confirmModalEl = document.getElementById("confirmModal");
    let pendingForm = null;

    if (confirmModalEl && hasBootstrap) {
        const confirmModal = window.bootstrap.Modal.getOrCreateInstance(confirmModalEl);
        const titleEl = document.getElementById("confirmTitle");
        const messageEl = document.getElementById("confirmMessage");
        const acceptBtn = document.getElementById("confirmAccept");
        const iconEl = confirmModalEl.querySelector(".confirm-icon");

        document.addEventListener("submit", function (event) {
            const form = event.target;
            if (!form.matches("form[data-confirm]") || form.dataset.confirmed === "true") {
                return;
            }
            event.preventDefault();
            pendingForm = form;
            titleEl.textContent = form.dataset.confirmTitle || "Are you sure?";
            messageEl.textContent = form.dataset.confirm;
            acceptBtn.textContent = form.dataset.confirmButton || "Confirm";
            const positive = form.dataset.confirmTone === "positive";
            iconEl.classList.toggle("ok", positive);
            iconEl.innerHTML = positive ? '<i class="bi bi-arrow-return-left"></i>'
                                        : '<i class="bi bi-exclamation-triangle"></i>';
            confirmModal.show();
        }, true);

        acceptBtn.addEventListener("click", function () {
            if (!pendingForm) {
                return;
            }
            const form = pendingForm;
            pendingForm = null;
            form.dataset.confirmed = "true";
            acceptBtn.disabled = true;
            acceptBtn.innerHTML = '<span class="spinner-border spinner-inline me-2"></span>Working...';
            setLoading(form);
            form.submit();
        });

        confirmModalEl.addEventListener("hidden.bs.modal", function () {
            acceptBtn.disabled = false;
            pendingForm = null;
        });
    } else {
        // Fallback when Bootstrap's script is unavailable: use the browser's confirm box.
        document.addEventListener("submit", function (event) {
            const form = event.target;
            if (form.matches("form[data-confirm]") && !window.confirm(form.dataset.confirm)) {
                event.preventDefault();
            }
        }, true);
    }

    // Loading state for ordinary forms (runs after the confirm handler).
    document.addEventListener("submit", function (event) {
        const form = event.target;
        if (event.defaultPrevented || form.matches("form[data-confirm]")) {
            return;
        }
        setLoading(form);
    });

    /* ---------- Show / hide password ---------- */
    document.querySelectorAll("[data-toggle-password]").forEach(function (btn) {
        btn.addEventListener("click", function () {
            const input = document.getElementById(btn.dataset.togglePassword);
            if (!input) {
                return;
            }
            const show = input.type === "password";
            input.type = show ? "text" : "password";
            btn.innerHTML = show ? '<i class="bi bi-eye-slash"></i>' : '<i class="bi bi-eye"></i>';
            btn.setAttribute("aria-label", show ? "Hide password" : "Show password");
        });
    });

    /* ---------- Demo credentials (login page) ---------- */
    document.querySelectorAll("[data-fill-demo]").forEach(function (btn) {
        btn.addEventListener("click", function () {
            const email = document.getElementById("email");
            const password = document.getElementById("password");
            if (email && password) {
                email.value = btn.dataset.email;
                password.value = btn.dataset.password;
                password.focus();
            }
        });
    });

    /* ---------- Clickable table rows ---------- */
    document.querySelectorAll("tr[data-href]").forEach(function (row) {
        row.addEventListener("click", function (event) {
            if (event.target.closest("a, button, form, input, select")) {
                return;
            }
            window.location.href = row.dataset.href;
        });
    });

    /* ---------- Filters that submit on change ---------- */
    document.querySelectorAll("[data-autosubmit]").forEach(function (el) {
        el.addEventListener("change", function () {
            if (el.form) {
                el.form.submit();
            }
        });
    });

    /* ---------- Progress bars (width from data attribute) ---------- */
    document.querySelectorAll("[data-width]").forEach(function (el) {
        const value = Math.max(0, Math.min(100, parseFloat(el.dataset.width) || 0));
        requestAnimationFrame(function () {
            el.style.width = value + "%";
        });
    });

    /* ---------- Type-to-filter for long select lists ---------- */
    document.querySelectorAll("[data-filter-select]").forEach(function (input) {
        const select = document.getElementById(input.dataset.filterSelect);
        if (!select) {
            return;
        }
        input.addEventListener("input", function () {
            const term = input.value.trim().toLowerCase();
            let firstMatch = null;
            Array.from(select.options).forEach(function (option) {
                if (!option.value) {
                    return;
                }
                const match = option.text.toLowerCase().includes(term);
                option.hidden = !match;
                if (match && !firstMatch) {
                    firstMatch = option;
                }
            });
            if (term && firstMatch && (select.selectedIndex <= 0 || select.options[select.selectedIndex].hidden)) {
                select.value = firstMatch.value;
                select.dispatchEvent(new Event("change"));
            }
        });
    });

    /* ---------- Issue Book: member limit, due date ---------- */
    const memberSelect = document.getElementById("memberId");
    const memberInfo = document.getElementById("memberInfo");
    if (memberSelect && memberInfo) {
        const maxLoans = parseInt(memberInfo.dataset.max, 10) || 3;
        const updateMemberInfo = function () {
            const option = memberSelect.options[memberSelect.selectedIndex];
            if (!option || !option.value) {
                memberInfo.className = "member-info";
                return;
            }
            const loans = parseInt(option.dataset.loans, 10) || 0;
            let dots = "";
            for (let i = 0; i < maxLoans; i++) {
                dots += '<span class="' + (i < loans ? "on" : "") + '"></span>';
            }
            const atLimit = loans >= maxLoans;
            memberInfo.className = "member-info show" + (atLimit ? " limit" : "");
            memberInfo.innerHTML = '<span class="loan-dots">' + dots + "</span><span>"
                + (atLimit
                    ? "<strong>Borrowing limit reached.</strong> This member already holds " + loans + " books."
                    : "Currently holds <strong>" + loans + "</strong> of " + maxLoans + " books allowed.")
                + "</span>";
        };
        memberSelect.addEventListener("change", updateMemberInfo);
        updateMemberInfo();
    }

    const issueDate = document.getElementById("issueDate");
    const dueDate = document.getElementById("dueDate");
    if (issueDate && dueDate) {
        const loanDays = parseInt(dueDate.dataset.loanDays, 10) || 14;
        issueDate.addEventListener("change", function () {
            if (!issueDate.value) {
                return;
            }
            const date = new Date(issueDate.value + "T00:00:00");
            date.setDate(date.getDate() + loanDays);
            const month = String(date.getMonth() + 1).padStart(2, "0");
            const day = String(date.getDate()).padStart(2, "0");
            dueDate.value = date.getFullYear() + "-" + month + "-" + day;
            dueDate.min = issueDate.value;
        });
    }

    /* ---------- Return receipt dialog ---------- */
    const receiptEl = document.getElementById("returnReceipt");
    if (receiptEl && hasBootstrap) {
        window.bootstrap.Modal.getOrCreateInstance(receiptEl).show();
    }

    /* ---------- Print ---------- */
    document.querySelectorAll("[data-print]").forEach(function (btn) {
        btn.addEventListener("click", function () {
            window.print();
        });
    });
})();
