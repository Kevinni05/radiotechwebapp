window.RadioTechRefreshCoordinator = function ({ run, canRefresh }) {
    let pending = null;
    return {
        refresh(silent = false) {
            if (silent && !canRefresh()) return Promise.resolve();
            if (pending) return pending;
            pending = Promise.resolve().then(() => run(silent)).finally(() => { pending = null; });
            return pending;
        }
    };
};
