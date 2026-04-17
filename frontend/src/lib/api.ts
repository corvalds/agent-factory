const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
const API_KEY = process.env.NEXT_PUBLIC_API_KEY || "dev-api-key-change-me";

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${API_KEY}`,
      ...options.headers,
    },
  });
  if (!res.ok) {
    throw new Error(`API error: ${res.status} ${res.statusText}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

export interface Task {
  id: number;
  name: string;
  description?: string;
  background?: string;
  goal?: string;
  acceptanceCriteria?: string;
  agentType: string;
  modelId?: string;
  sandboxEnabled: boolean;
  status: "PENDING" | "ANALYZING" | "RUNNING" | "COMPLETED" | "FAILED";
  result?: string;
  error?: string;
  startedAt?: string;
  completedAt?: string;
  createdAt: string;
}

export interface Provider {
  id: number;
  name: string;
  type: "OPENAI" | "ANTHROPIC" | "DEEPSEEK" | "CUSTOM";
  baseUrl?: string;
  models: string;
  active: boolean;
  createdAt: string;
}

export const api = {
  tasks: {
    list: () => request<Task[]>("/api/tasks"),
    get: (id: number) => request<Task>(`/api/tasks/${id}`),
    create: (task: Partial<Task>) => request<Task>("/api/tasks", { method: "POST", body: JSON.stringify(task) }),
    clone: (id: number) => request<Task>(`/api/tasks/clone/${id}`, { method: "POST" }),
    updateStatus: (id: number, status: string) =>
      request<Task>(`/api/tasks/${id}/status`, { method: "PATCH", body: JSON.stringify({ status }) }),
  },
  providers: {
    list: () => request<Provider[]>("/api/providers"),
    get: (id: number) => request<Provider>(`/api/providers/${id}`),
    create: (provider: Partial<Provider> & { apiKey: string }) =>
      request<Provider>("/api/providers", { method: "POST", body: JSON.stringify(provider) }),
    update: (id: number, provider: Partial<Provider>) =>
      request<Provider>(`/api/providers/${id}`, { method: "PUT", body: JSON.stringify(provider) }),
    delete: (id: number) => request<void>(`/api/providers/${id}`, { method: "DELETE" }),
    test: (id: number) => request<{ success: boolean; message: string }>(`/api/providers/${id}/test`, { method: "POST" }),
  },
};
