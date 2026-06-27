const API = '/api/v1/todos';

document.addEventListener('alpine:init', () => {
  Alpine.data('todoApp', () => ({
    // --- state ---
    todos: [],
    filter: 'all',     // 'all' | 'active' | 'completed'
    newTitle: '',
    error: null,

    // --- lifecycle ---
    init() {
      this.reload();
    },

    // --- API ラッパー ---
    async reload() {
      const q = this.filter === 'all'    ? ''
              : this.filter === 'active' ? '?completed=false'
              :                            '?completed=true';
      const res = await fetch(API + q);
      if (!res.ok) return this.handleError(res);
      this.todos = await res.json();
      this.error = null;
    },

    async create() {
      const title = this.newTitle.trim();
      if (!title) return;
      const res = await fetch(API, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title }),
      });
      if (!res.ok) return this.handleError(res);
      this.newTitle = '';
      this.reload();
    },

    async toggle(t) {
      const res = await fetch(`${API}/${t.id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ completed: !t.completed }),
      });
      if (!res.ok) return this.handleError(res);
      this.reload();
    },

    async remove(id) {
      const res = await fetch(`${API}/${id}`, { method: 'DELETE' });
      if (!res.ok) return this.handleError(res);
      this.reload();
    },

    async clearCompleted() {
      const res = await fetch(`${API}?completed=true`, { method: 'DELETE' });
      if (!res.ok) return this.handleError(res);
      this.reload();
    },

    // --- ProblemDetail を解釈してエラー文字列に変換 ---
    async handleError(res) {
      try {
        const p = await res.json();
        this.error = p.errors?.map(e => `${e.field}: ${e.message}`).join(' / ')
                  ?? p.detail
                  ?? `${p.title} (${p.status})`;
      } catch {
        this.error = `${res.status} ${res.statusText}`;
      }
    },
  }));
});
