/* ==========================================================================
   LibraTrack — charts
   Uses Chart.js when it is available (CDN). If the browser is offline, a small
   built-in canvas renderer draws the same charts so the demo still works.
   Usage: LibraCharts.line("canvasId", labels, values, { label: "Books issued" })
   ========================================================================== */
window.LibraCharts = (function () {
    "use strict";

    const COLORS = {
        terracotta: "#C65D32",
        navy: "#19313B",
        grid: "rgba(24, 36, 42, 0.07)",
        text: "#77736D",
        palette: ["#C65D32", "#19313B", "#D9A15B", "#5A9C6E", "#7E9CB3", "#B5786B", "#8C7A5B", "#4F6D7A"]
    };
    const FONT = "12px Inter, system-ui, -apple-system, 'Segoe UI', sans-serif";

    function hasChartJs() {
        return typeof window.Chart !== "undefined";
    }

    function canvasById(id) {
        return document.getElementById(id);
    }

    /* ======================= Chart.js versions ======================= */

    function applyDefaults() {
        if (!hasChartJs()) {
            return;
        }
        Chart.defaults.font.family = "Inter, system-ui, sans-serif";
        Chart.defaults.font.size = 12;
        Chart.defaults.color = COLORS.text;
        Chart.defaults.plugins.legend.display = false;
        Chart.defaults.plugins.tooltip.backgroundColor = COLORS.navy;
        Chart.defaults.plugins.tooltip.padding = 10;
        Chart.defaults.plugins.tooltip.cornerRadius = 8;
        Chart.defaults.maintainAspectRatio = false;
    }

    function axes(options) {
        const prefix = (options && options.prefix) || "";
        return {
            x: { grid: { display: false }, border: { display: false } },
            y: {
                beginAtZero: true,
                grid: { color: COLORS.grid },
                border: { display: false },
                ticks: { precision: 0, callback: function (value) { return prefix + value; } }
            }
        };
    }

    function chartJsLine(canvas, labels, values, options) {
        const ctx = canvas.getContext("2d");
        const gradient = ctx.createLinearGradient(0, 0, 0, canvas.parentElement.clientHeight || 260);
        gradient.addColorStop(0, "rgba(198, 93, 50, 0.28)");
        gradient.addColorStop(1, "rgba(198, 93, 50, 0.02)");
        return new Chart(ctx, {
            type: "line",
            data: {
                labels: labels,
                datasets: [{
                    label: (options && options.label) || "Value",
                    data: values,
                    borderColor: COLORS.terracotta,
                    backgroundColor: gradient,
                    fill: true,
                    tension: 0.35,
                    borderWidth: 2.5,
                    pointRadius: 4.5,
                    pointHoverRadius: 6,
                    pointBackgroundColor: COLORS.terracotta,
                    pointBorderColor: "#FFFFFF",
                    pointBorderWidth: 2
                }]
            },
            options: { scales: axes(options), interaction: { intersect: false, mode: "index" } }
        });
    }

    function chartJsBar(canvas, labels, values, options) {
        const horizontal = options && options.horizontal;
        const scales = axes(options);
        if (horizontal) {
            scales.x = { beginAtZero: true, grid: { color: COLORS.grid }, border: { display: false }, ticks: { precision: 0 } };
            scales.y = { grid: { display: false }, border: { display: false } };
        }
        return new Chart(canvas, {
            type: "bar",
            data: {
                labels: labels,
                datasets: [{
                    label: (options && options.label) || "Value",
                    data: values,
                    backgroundColor: (options && options.color) || COLORS.terracotta,
                    hoverBackgroundColor: COLORS.navy,
                    borderRadius: 8,
                    maxBarThickness: horizontal ? 22 : 34
                }]
            },
            options: { indexAxis: horizontal ? "y" : "x", scales: scales }
        });
    }

    function chartJsDoughnut(canvas, labels, values) {
        return new Chart(canvas, {
            type: "doughnut",
            data: {
                labels: labels,
                datasets: [{ data: values, backgroundColor: COLORS.palette, borderColor: "#FBF8F3", borderWidth: 3, hoverOffset: 6 }]
            },
            options: {
                cutout: "62%",
                plugins: {
                    legend: {
                        display: true,
                        position: "right",
                        labels: { usePointStyle: true, pointStyle: "circle", boxWidth: 8, padding: 14 }
                    }
                }
            }
        });
    }

    /* ======================= Offline fallback renderer ======================= */

    function prepare(canvas) {
        const box = canvas.parentElement;
        const width = box.clientWidth || 400;
        const height = box.clientHeight || 260;
        const ratio = window.devicePixelRatio || 1;
        canvas.width = width * ratio;
        canvas.height = height * ratio;
        canvas.style.width = width + "px";
        canvas.style.height = height + "px";
        const ctx = canvas.getContext("2d");
        ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
        ctx.clearRect(0, 0, width, height);
        ctx.font = FONT;
        return { ctx: ctx, w: width, h: height };
    }

    function niceMax(value) {
        if (value <= 0) {
            return 4;
        }
        const power = Math.pow(10, Math.floor(Math.log10(value)));
        const n = value / power;
        const step = n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10;
        return step * power;
    }

    function label(value) {
        return Number.isInteger(value) ? String(value) : value.toFixed(1);
    }

    function truncate(ctx, text, maxWidth) {
        let t = String(text);
        if (ctx.measureText(t).width <= maxWidth) {
            return t;
        }
        while (t.length > 1 && ctx.measureText(t + "…").width > maxWidth) {
            t = t.slice(0, -1);
        }
        return t + "…";
    }

    function roundedRect(ctx, x, y, w, h, r) {
        const radius = Math.max(0, Math.min(r, w / 2, h / 2));
        ctx.beginPath();
        ctx.moveTo(x + radius, y);
        ctx.arcTo(x + w, y, x + w, y + h, radius);
        ctx.arcTo(x + w, y + h, x, y + h, radius);
        ctx.arcTo(x, y + h, x, y, radius);
        ctx.arcTo(x, y, x + w, y, radius);
        ctx.closePath();
    }

    function valueGrid(ctx, area, max, prefix, horizontal) {
        ctx.strokeStyle = COLORS.grid;
        ctx.fillStyle = COLORS.text;
        ctx.lineWidth = 1;
        for (let i = 0; i <= 4; i++) {
            const v = max * i / 4;
            ctx.beginPath();
            if (horizontal) {
                const x = area.left + (area.right - area.left) * i / 4;
                ctx.moveTo(x, area.top);
                ctx.lineTo(x, area.bottom);
                ctx.stroke();
                ctx.textAlign = "center";
                ctx.textBaseline = "top";
                ctx.fillText(prefix + label(v), x, area.bottom + 8);
            } else {
                const y = area.bottom - (area.bottom - area.top) * i / 4;
                ctx.moveTo(area.left, y);
                ctx.lineTo(area.right, y);
                ctx.stroke();
                ctx.textAlign = "right";
                ctx.textBaseline = "middle";
                ctx.fillText(prefix + label(v), area.left - 8, y);
            }
        }
    }

    function fallbackLine(canvas, labels, values, options) {
        const s = prepare(canvas);
        const ctx = s.ctx;
        const nums = values.map(Number);
        const max = niceMax(Math.max.apply(null, nums.concat([0])));
        const area = { left: 48, right: s.w - 14, top: 14, bottom: s.h - 28 };
        valueGrid(ctx, area, max, (options && options.prefix) || "", false);

        const step = labels.length > 1 ? (area.right - area.left) / (labels.length - 1) : 0;
        const points = nums.map(function (v, i) {
            return { x: area.left + step * i, y: area.bottom - (area.bottom - area.top) * v / max };
        });
        if (points.length) {
            const fill = ctx.createLinearGradient(0, area.top, 0, area.bottom);
            fill.addColorStop(0, "rgba(198, 93, 50, 0.26)");
            fill.addColorStop(1, "rgba(198, 93, 50, 0.02)");
            ctx.beginPath();
            ctx.moveTo(points[0].x, area.bottom);
            points.forEach(function (p) { ctx.lineTo(p.x, p.y); });
            ctx.lineTo(points[points.length - 1].x, area.bottom);
            ctx.closePath();
            ctx.fillStyle = fill;
            ctx.fill();

            ctx.beginPath();
            points.forEach(function (p, i) { if (i === 0) { ctx.moveTo(p.x, p.y); } else { ctx.lineTo(p.x, p.y); } });
            ctx.strokeStyle = COLORS.terracotta;
            ctx.lineWidth = 2.5;
            ctx.lineJoin = "round";
            ctx.stroke();

            points.forEach(function (p) {
                ctx.beginPath();
                ctx.arc(p.x, p.y, 4.5, 0, Math.PI * 2);
                ctx.fillStyle = COLORS.terracotta;
                ctx.fill();
                ctx.lineWidth = 2;
                ctx.strokeStyle = "#FFFFFF";
                ctx.stroke();
            });
        }
        ctx.fillStyle = COLORS.text;
        ctx.textAlign = "center";
        ctx.textBaseline = "top";
        labels.forEach(function (l, i) { ctx.fillText(l, area.left + step * i, area.bottom + 10); });
    }

    function fallbackBar(canvas, labels, values, options) {
        const s = prepare(canvas);
        const ctx = s.ctx;
        const nums = values.map(Number);
        const max = niceMax(Math.max.apply(null, nums.concat([0])));
        const color = (options && options.color) || COLORS.terracotta;
        const horizontal = options && options.horizontal;
        const n = Math.max(1, labels.length);

        if (horizontal) {
            const labelWidth = Math.min(200, s.w * 0.42);
            const area = { left: labelWidth + 12, right: s.w - 16, top: 6, bottom: s.h - 26 };
            valueGrid(ctx, area, max, "", true);
            const band = (area.bottom - area.top) / n;
            const thickness = Math.min(22, band * 0.62);
            nums.forEach(function (v, i) {
                const y = area.top + band * i + (band - thickness) / 2;
                const width = (area.right - area.left) * v / max;
                ctx.fillStyle = color;
                roundedRect(ctx, area.left, y, Math.max(width, 2), thickness, 6);
                ctx.fill();
                ctx.fillStyle = COLORS.text;
                ctx.textAlign = "right";
                ctx.textBaseline = "middle";
                ctx.fillText(truncate(ctx, labels[i], labelWidth), area.left - 10, y + thickness / 2);
            });
        } else {
            const area = { left: 48, right: s.w - 10, top: 14, bottom: s.h - 28 };
            valueGrid(ctx, area, max, (options && options.prefix) || "", false);
            const band = (area.right - area.left) / n;
            const thickness = Math.min(34, band * 0.6);
            nums.forEach(function (v, i) {
                const x = area.left + band * i + (band - thickness) / 2;
                const height = (area.bottom - area.top) * v / max;
                ctx.fillStyle = color;
                roundedRect(ctx, x, area.bottom - height, thickness, Math.max(height, 2), 7);
                ctx.fill();
                ctx.fillStyle = COLORS.text;
                ctx.textAlign = "center";
                ctx.textBaseline = "top";
                ctx.fillText(labels[i], x + thickness / 2, area.bottom + 10);
            });
        }
    }

    function fallbackDoughnut(canvas, labels, values) {
        const s = prepare(canvas);
        const ctx = s.ctx;
        const nums = values.map(Number);
        const total = nums.reduce(function (a, b) { return a + b; }, 0) || 1;
        const radius = Math.min(s.h / 2 - 10, s.w * 0.28);
        const cx = radius + 16;
        const cy = s.h / 2;
        let angle = -Math.PI / 2;
        nums.forEach(function (v, i) {
            const slice = (v / total) * Math.PI * 2;
            ctx.beginPath();
            ctx.moveTo(cx, cy);
            ctx.arc(cx, cy, radius, angle, angle + slice);
            ctx.closePath();
            ctx.fillStyle = COLORS.palette[i % COLORS.palette.length];
            ctx.fill();
            ctx.lineWidth = 3;
            ctx.strokeStyle = "#FBF8F3";
            ctx.stroke();
            angle += slice;
        });
        ctx.beginPath();
        ctx.arc(cx, cy, radius * 0.62, 0, Math.PI * 2);
        ctx.fillStyle = "#FBF8F3";
        ctx.fill();

        const legendX = cx + radius + 28;
        const lineHeight = 24;
        let y = cy - (labels.length * lineHeight) / 2 + lineHeight / 2;
        ctx.textAlign = "left";
        ctx.textBaseline = "middle";
        labels.forEach(function (l, i) {
            ctx.beginPath();
            ctx.arc(legendX, y, 4.5, 0, Math.PI * 2);
            ctx.fillStyle = COLORS.palette[i % COLORS.palette.length];
            ctx.fill();
            ctx.fillStyle = COLORS.text;
            ctx.fillText(truncate(ctx, l + " (" + nums[i] + ")", s.w - legendX - 16), legendX + 12, y);
            y += lineHeight;
        });
    }

    function drawFallback(draw, canvas, args) {
        const render = function () { draw.apply(null, [canvas].concat(args)); };
        render();
        let timer = null;
        window.addEventListener("resize", function () {
            clearTimeout(timer);
            timer = setTimeout(render, 150);
        });
        return null;
    }

    /* ======================= Public API ======================= */

    function line(id, labels, values, options) {
        const canvas = canvasById(id);
        if (!canvas) { return null; }
        return hasChartJs() ? chartJsLine(canvas, labels, values, options)
                            : drawFallback(fallbackLine, canvas, [labels, values, options]);
    }

    function bar(id, labels, values, options) {
        const canvas = canvasById(id);
        if (!canvas) { return null; }
        return hasChartJs() ? chartJsBar(canvas, labels, values, options)
                            : drawFallback(fallbackBar, canvas, [labels, values, options]);
    }

    function doughnut(id, labels, values) {
        const canvas = canvasById(id);
        if (!canvas) { return null; }
        return hasChartJs() ? chartJsDoughnut(canvas, labels, values)
                            : drawFallback(fallbackDoughnut, canvas, [labels, values]);
    }

    applyDefaults();

    return { line: line, bar: bar, doughnut: doughnut, colors: COLORS };
})();
