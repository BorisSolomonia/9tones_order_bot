function parsePositiveInt(value: string | undefined, fallback: number): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? Math.floor(parsed) : fallback;
}

export const FRONTEND_CONFIG = {
  query: {
    staleTimeMs: parsePositiveInt(process.env.NEXT_PUBLIC_QUERY_STALE_TIME_MS, 30000),
    retryCount: parsePositiveInt(process.env.NEXT_PUBLIC_QUERY_RETRY_COUNT, 1),
  },
  search: {
    debounceMs: parsePositiveInt(process.env.NEXT_PUBLIC_SEARCH_DEBOUNCE_MS, 300),
  },
  customers: {
    pageSize: parsePositiveInt(process.env.NEXT_PUBLIC_CUSTOMERS_PAGE_SIZE, 1000),
    adminPageSize: parsePositiveInt(process.env.NEXT_PUBLIC_ADMIN_CUSTOMERS_PAGE_SIZE, 1000),
  },
} as const;
