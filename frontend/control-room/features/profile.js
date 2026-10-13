// profile: presentation and operations for this feature.
export function createFeature(ctx){
/* ---------- USER / PROFILE ---------- */

            function applyPermissions() {
                const allowed = ["ADMIN", "SUPER_ADMIN", "CHIEF_EXECUTIVE", "CAPO", "NETWORK_MANAGER", "ENGINEER"].includes(ctx.state.authRole || ctx.state.user?.role);
                for (const id of ["quickAntennaBtn", "quickNotificationBtn", "newAntennaBtn", "newOperatorBtn", "syncOperatorBtn", "createTaskBtn", "newNotificationBtn", "sendNotificationBtn", "newInventoryItemBtn", "editProfileBtn"]) {
                    if (ctx.$(id)) ctx.$(id).hidden = !allowed;
                }
                document.querySelectorAll("[data-edit], [data-delete], [data-delete-operator], [data-reset-password], [data-validity], [data-save-role], [data-approve], [data-badge], [data-regenerate], [data-inventory-move], [data-inventory-edit], [data-inventory-archive], [data-inventory-restore]").forEach(element => { element.hidden = !allowed; });
                document.querySelectorAll("[data-report-approve], [data-report-reject], [data-report-delete]").forEach(element => { element.hidden = !ctx.canManageReports(); });
                document.querySelectorAll('[data-notification-retry]').forEach(element=>{element.hidden=!allowed;});
                const accountAdministration=allowed&&(ctx.state.authRole||ctx.state.user?.role)!=='ENGINEER';
                for(const id of ['newOperatorBtn','syncOperatorBtn'])if(ctx.$(id))ctx.$(id).hidden=!accountAdministration;
                document.querySelectorAll('[data-delete-operator],[data-reset-password],[data-validity],[data-save-role],[data-approve],[data-regenerate]').forEach(element=>{element.hidden=!accountAdministration;});
                document.querySelectorAll('[data-operator-role]').forEach(element=>{element.disabled=!accountAdministration||element.dataset.roleLocked==='true';});
            }

            function hydrateUser() {
                applyPermissions();
                ctx.signatureWorkspace?.syncIdentity();
                const u = ctx.state.user || {};
                const name = u.name || u.displayName || u.fullName || "Capo RadioTech";
                const email = u.email || "—";
                const role = u.role || u.ruolo || "Chief Executive · RadioTech";

                ctx.setText("workspaceTenant", `Azienda ${u.tenantId || "—"}`);
                if(ctx.$("workspaceTenant"))ctx.$("workspaceTenant").title=u.tenantId || "Azienda non disponibile";
                const avatar = ctx.initials(name);

                ctx.setText("miniAvatar", avatar);
                ctx.setText("miniName", name);
                ctx.setText("miniEmail", email);

                ctx.setText("profileAvatar", avatar);
                window.RadioTechPersonalization?.syncProfile(ctx.state.user);
                ctx.setText("profileName", name);
                ctx.setText("profileFullName", name);
                ctx.setText("profileEmail", email);
                ctx.setText("profilePhone", u.phone || u.telefono || "—");
                ctx.setText("profileRole", role);
                ctx.setText("profileRoleField", role);
                ctx.setText("profileUid", u.uid || u.id || "—");
                ctx.setText("profileLastLogin", u.lastLogin ? new Date(u.lastLogin).toLocaleString("it-IT") : "Sessione corrente");

                ctx.setText("sessionUser", email);
                ctx.setText("sessionTokenStatus", ctx.state.token ? "Token presente" : "Nessun token");
                ctx.setText("sessionPersistence", localStorage.getItem(ctx.CONFIG.STORAGE_REMEMBER) === "true" ? "ATTIVA" : "SESSIONE");
            }

            function persistUser() { /* Profile belongs to the current in-memory session. */ }
            async function loadProfile() {
                try {
                    const profile = await ctx.api("/profile");
                    if (profile) {
                        const data = profile.data || profile.user || profile;
                        ctx.state.user = { ...ctx.state.user, ...data, name: data.fullName || data.name || ctx.state.user?.name, role: ctx.state.authRole || ctx.state.user?.role };
                        persistUser();
                        hydrateUser();
                    }
                } catch (error) {
                    console.warn("Profilo non disponibile:", error.message);
                }
            }

            async function saveProfile(event) {
                event.preventDefault();

                const payload = {
                    fullName: ctx.$("editProfileName").value.trim(),
                    phone: ctx.$("editProfilePhone").value.trim(),
                    company: ctx.$("editProfileCompany").value.trim(),
                    photoUrl: window.RadioTechPersonalization?.profilePhoto() ?? ctx.state.user?.photoUrl ?? ""
                };

                try {
                    const result = await ctx.apiFetch("/api/v1/capo/profile", {
                        method: "PUT",
                        body: JSON.stringify(payload)
                    });

                    const data = result?.data || result?.user || result || payload;
                    ctx.state.user = { ...ctx.state.user, ...data, name: data.fullName || ctx.state.user?.name, role: ctx.state.authRole || ctx.state.user?.role };
                    persistUser();
                    hydrateUser();
                    ctx.closeModal("profileModal");
                    ctx.toast("Profilo aggiornato", "Le informazioni del Capo sono state salvate.");
                } catch (error) {
                    window.RadioTechForms.errors(ctx.$("profileForm"),error.fieldErrors);
                    ctx.toast("Profilo non aggiornato", error.message, "error");
                }
            }

            function openProfileModal() {
                applyPermissions();
                const u = ctx.state.user || {};
                ctx.$("editProfileName").value = u.name || u.displayName || u.fullName || "";
                ctx.$("editProfilePhone").value = u.phone || u.telefono || "";
                ctx.$("editProfileCompany").value = u.company || "";
                window.RadioTechPersonalization?.resetProfile(u);
                ctx.openModal("profileModal");
            }
return {get applyPermissions(){return applyPermissions;},get hydrateUser(){return hydrateUser;},get persistUser(){return persistUser;},get loadProfile(){return loadProfile;},get saveProfile(){return saveProfile;},get openProfileModal(){return openProfileModal;}};
}
