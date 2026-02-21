'use client';

import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import type { Customer, MyCustomer } from '@/types';
import { useState, useEffect } from 'react';
import { FRONTEND_CONFIG } from '@/lib/config';

export function useCustomers(tab: 'all' | 'my' = 'all') {
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), FRONTEND_CONFIG.search.debounceMs);
    return () => clearTimeout(timer);
  }, [search]);

  const { data, isLoading, error } = useQuery({
    queryKey: ['customers', tab, debouncedSearch],
    queryFn: () =>
      api.get<Customer[]>(
        `/api/v1/customers?search=${encodeURIComponent(debouncedSearch)}&tab=${tab}&size=${FRONTEND_CONFIG.customers.pageSize}`
      ),
  });

  return { customers: data ?? [], isLoading, error, search, setSearch };
}

export function useMyCustomers() {
  return useQuery({
    queryKey: ['my-customers'],
    queryFn: () => api.get<MyCustomer[]>('/api/v1/customers/my'),
  });
}

export function useFrequentCustomers(limit = 20) {
  return useQuery({
    queryKey: ['frequent-customers', limit],
    queryFn: () => api.get<Customer[]>(`/api/v1/customers/frequent?limit=${limit}`),
  });
}
