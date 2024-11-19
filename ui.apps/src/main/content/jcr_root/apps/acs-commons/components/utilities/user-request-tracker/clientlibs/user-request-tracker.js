document.addEventListener("DOMContentLoaded", function () {
    const endpoint = "/apps/acs-commons/content/user-request-tracker/_jcr_content.request-api.json";

    let selectedInstanceKey = null; // Keep track of the selected instance

    const intervalOrder = ["LastMinute", "LastHour", "LastDay", "LastWeek", "LastMonth", "LastYear", "Forever"];

    function downloadCSV(filename, data) {
        const csvContent = [
            ["Interval", "UserID", "First Name", "Last Name", "Email", "Request Count"],
            ...data.map(row => [
                row.interval || "N/A",
                row.UserID || "N/A",
                row.firstName || "N/A",
                row.lastName || "N/A",
                row.eMail || "N/A",
                row.requestCount || 0,
            ]),
        ]
            .map(e => e.join(","))
            .join("\n");

        const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
        const link = document.createElement("a");
        link.href = URL.createObjectURL(blob);
        link.download = filename;
        link.click();
    }

    function downloadInstanceData(instanceData, instanceName) {
        const csvContent = [];

        Object.keys(instanceData).forEach(interval => {
            const rows = Object.values(instanceData[interval]).map(row => ({
                interval,
                ...row,
            }));
            csvContent.push(...rows);
        });

        downloadCSV(`${instanceName.replace(/\s+/g, "_")}_instance.csv`, csvContent);
    }

    function createTable(rows, interval) {
        const container = document.createElement("div");

        const copyButton = document.createElement("button");
        copyButton.className = "coral-Button coral-Button--primary";
        copyButton.textContent = "Copy Emails";
        copyButton.style.marginBottom = "10px";
        copyButton.addEventListener("click", () => {
            const emails = Object.values(rows)
                .map(user => user.eMail)
                .filter(email => email)
                .join(", ");
            navigator.clipboard.writeText(emails)
                .then(() => alert("Emails copied to clipboard!"))
                .catch(err => console.error("Failed to copy emails:", err));
        });
        container.appendChild(copyButton);

        const downloadButton = document.createElement("button");
        downloadButton.className = "coral-Button coral-Button--secondary";
        downloadButton.textContent = "Download CSV";
        downloadButton.style.marginBottom = "10px";
        downloadButton.style.marginLeft = "10px";
        downloadButton.addEventListener("click", () => {
            const data = Object.values(rows).map(row => ({ interval, ...row }));
            downloadCSV(`${interval.replace(/\s+/g, "_")}.csv`, data);
        });
        container.appendChild(downloadButton);

        const table = document.createElement("table");
        table.className = "coral-Table coral-Table--bordered coral-Table--striped";
        table.style.marginTop = "10px";

        const thead = document.createElement("thead");
        thead.innerHTML = `
            <tr>
                <th>UserID</th>
                <th>First Name</th>
                <th>Last Name</th>
                <th>Email</th>
                <th>Request Count</th>
            </tr>`;
        table.appendChild(thead);

        const tbody = document.createElement("tbody");

        const sortedRows = Object.values(rows).sort((a, b) => b.requestCount - a.requestCount);
        sortedRows.forEach(row => {
            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td>${row.UserID || "N/A"}</td>
                <td>${row.firstName || "N/A"}</td>
                <td>${row.lastName || "N/A"}</td>
                <td>${row.eMail || "N/A"}</td>
                <td>${row.requestCount || 0}</td>`;
            tbody.appendChild(tr);
        });
        table.appendChild(tbody);

        container.appendChild(table);
        return container;
    }

    function updateIntervalTabs(instanceData) {
        const tabList = document.getElementById("interval-tab-list");
        const tabPanels = document.getElementById("interval-tab-panels");

        tabList.innerHTML = "";
        tabPanels.innerHTML = "";

        const sortedIntervals = Object.keys(instanceData)
            .map(interval => ({
                original: interval,
                cleaned: interval.replace(/^\d+\s/, ""),
            }))
            .sort((a, b) => intervalOrder.indexOf(a.cleaned) - intervalOrder.indexOf(b.cleaned));

        sortedIntervals.forEach(({ original, cleaned }, index) => {
            const tab = document.createElement("coral-tab");
            tab.textContent = cleaned;
            tab.setAttribute("aria-controls", `interval-panel-${index}`);
            if (index === 0) tab.setAttribute("selected", true);
            tabList.appendChild(tab);

            const panel = document.createElement("coral-panel");
            panel.id = `interval-panel-${index}`;
            panel.className = "coral3-Panel";
            if (index === 0) panel.setAttribute("selected", true);

            const table = createTable(instanceData[original], cleaned);
            panel.appendChild(table);
            tabPanels.appendChild(panel);
        });

        tabList.addEventListener("coral-tablist:change", (event) => {
            const selectedTab = event.target.selectedItem;
            const selectedPanelId = selectedTab.getAttribute("aria-controls");

            tabPanels.querySelectorAll("coral-panel").forEach(panel => {
                panel.id === selectedPanelId
                    ? panel.setAttribute("selected", true)
                    : panel.removeAttribute("selected");
            });
        });
    }

    function populateSidebar(instances) {
        const sidebarList = document.getElementById("sidebar-instance-list");
        const downloadInstanceButton = document.getElementById("download-instance-csv");
        const instanceTitle = document.getElementById("instance-title");

        sidebarList.innerHTML = "";
        Object.keys(instances).forEach((instanceKey, index) => {
            const listItem = document.createElement("li");
            const button = document.createElement("button");
            button.className = "coral-Button coral-Button--secondary";
            button.textContent = instanceKey === "Cluster" ? "Cluster" : instanceKey;

            if (index === 0) {
                button.classList.add("is-selected");
                selectedInstanceKey = instanceKey;
                updateIntervalTabs(instances[instanceKey]);
                instanceTitle.textContent = `Instance: ${instanceKey}`;
            }

            button.addEventListener("click", () => {
                sidebarList.querySelectorAll("button").forEach(btn => btn.classList.remove("is-selected"));
                button.classList.add("is-selected");

                updateIntervalTabs(instances[instanceKey]);
                selectedInstanceKey = instanceKey;
                instanceTitle.textContent = `Instance: ${instanceKey}`;
            });

            listItem.appendChild(button);
            sidebarList.appendChild(listItem);
        });

        downloadInstanceButton.onclick = () => {
            downloadInstanceData(instances[selectedInstanceKey], selectedInstanceKey);
        };
    }

    function reloadData() {
        console.log("Reloading data...");
        fetch(endpoint)
            .then(response => response.json())
            .then(data => {
                console.log("Data fetched:", data);
                populateSidebar(data);
            })
            .catch(error => console.error("Error reloading data:", error));
    }

    document.getElementById("reload-data").addEventListener("click", reloadData);

    fetch(endpoint)
        .then(response => response.json())
        .then(data => {
            populateSidebar(data);
        })
        .catch(error => console.error("Error fetching data:", error));
});
