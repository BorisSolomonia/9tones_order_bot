'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import type { Order, OrderItem, SelectedCustomer } from '@/types';

export function useOrders(date?: string, managerId?: string) {
  return useQuery({
    queryKey: ['orders', date, managerId],
    queryFn: () => {
      const params = new URLSearchParams();
      if (date) params.set('date', date);
      if (managerId) params.set('manager_id', managerId);
      params.set('size', '50');
      return api.get<Order[]>(`/api/v1/orders?${params}`);
    },
  });
}

export function useOrderDetail(id: string) {
  return useQuery({
    queryKey: ['order', id],
    queryFn: () => api.get<Order>(`/api/v1/orders/${id}`),
    enabled: !!id,
  });
}

export function useCreateOrder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: { items: SelectedCustomer[]; sendTelegram: boolean }) =>
      api.post<Order>('/api/v1/orders', {
        items: data.items.map((i) => ({
          customerName: i.customerName,
          customerId: i.customerId,
          comment: i.comment,
          board: i.board ?? null,
        })),
        sendTelegram: data.sendTelegram,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['orders'] });
    },
  });
}

export function useUpdateOrderItemBoard() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orderId, itemId, board }: { orderId: string; itemId: string; board: string | null }) =>
      api.patch<OrderItem>(`/api/v1/orders/${orderId}/items/${itemId}`, { board }),
    onSuccess: (_, { orderId }) => {
      queryClient.invalidateQueries({ queryKey: ['order', orderId] });
    },
  });
}
