'use client';

import { useState, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api, buildApiUrl } from '@/lib/api';
import { useOrderDetail, useUpdateOrderItemBoard } from '@/hooks/use-orders';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { GEO } from '@/lib/geo';
import { formatDate } from '@/lib/utils';
import { Download, ChevronDown, ChevronUp, Pencil, Check, X } from 'lucide-react';
import type { Order, OrderItem, User } from '@/types';

export default function ReportsPage() {
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [managerId, setManagerId] = useState('');
  const [expandedOrderId, setExpandedOrderId] = useState<string | null>(null);

  const { data: orders, isLoading } = useQuery({
    queryKey: ['orders', dateFrom, dateTo, managerId],
    queryFn: () => {
      const params = new URLSearchParams();
      if (dateFrom) params.set('date', dateFrom);
      if (managerId) params.set('manager_id', managerId);
      params.set('size', '100');
      return api.get<Order[]>(`/api/v1/orders?${params}`);
    },
  });

  const { data: users } = useQuery({
    queryKey: ['users-list'],
    queryFn: () => api.get<User[]>('/api/v1/users'),
  });

  const managers = users?.filter((u) => u.role === 'MANAGER') ?? [];

  const handleExport = () => {
    const params = new URLSearchParams();
    if (dateFrom) params.set('date_from', dateFrom);
    if (dateTo) params.set('date_to', dateTo);
    if (managerId) params.set('manager_id', managerId);
    params.set('format', 'csv');
    window.open(buildApiUrl(`/api/v1/orders/export?${params.toString()}`), '_blank');
  };

  return (
    <div className="p-4 space-y-4">
      <div className="flex flex-col gap-2 sm:flex-row">
        <input
          type="date"
          value={dateFrom}
          onChange={(e) => setDateFrom(e.target.value)}
          className="h-11 px-3 rounded-md border border-input bg-background text-sm flex-1"
          placeholder="დან"
        />
        <input
          type="date"
          value={dateTo}
          onChange={(e) => setDateTo(e.target.value)}
          className="h-11 px-3 rounded-md border border-input bg-background text-sm flex-1"
          placeholder="მდე"
        />
        <select
          value={managerId}
          onChange={(e) => setManagerId(e.target.value)}
          className="h-11 px-3 rounded-md border border-input bg-background text-sm flex-1"
        >
          <option value="">{GEO.manager} - ყველა</option>
          {managers.map((m) => (
            <option key={m.userId} value={m.userId}>{m.displayName}</option>
          ))}
        </select>
        <Button onClick={handleExport} variant="outline">
          <Download className="h-4 w-4 mr-2" />
          {GEO.export}
        </Button>
      </div>

      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-14 w-full" />
          ))}
        </div>
      ) : (
        <div className="border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-muted">
              <tr>
                <th className="px-4 py-3 text-left">{GEO.date}</th>
                <th className="px-4 py-3 text-left">{GEO.manager}</th>
                <th className="px-4 py-3 text-center">{GEO.customerCount}</th>
                <th className="px-4 py-3 text-center">{GEO.status}</th>
                <th className="px-4 py-3 text-center"></th>
              </tr>
            </thead>
            <tbody>
              {orders?.map((order) => (
                <>
                  <tr
                    key={order.orderId}
                    className="border-t cursor-pointer hover:bg-muted/30"
                    onClick={() => setExpandedOrderId(expandedOrderId === order.orderId ? null : order.orderId)}
                  >
                    <td className="px-4 py-3">{formatDate(order.date)}</td>
                    <td className="px-4 py-3">{order.managerName}</td>
                    <td className="px-4 py-3 text-center">{order.itemCount}</td>
                    <td className="px-4 py-3 text-center">
                      <Badge variant={order.status === 'SENT' ? 'success' : 'destructive'}>
                        {order.status === 'SENT' ? GEO.success : GEO.failed}
                      </Badge>
                    </td>
                    <td className="px-4 py-3 text-center">
                      {expandedOrderId === order.orderId
                        ? <ChevronUp className="h-4 w-4 mx-auto" />
                        : <ChevronDown className="h-4 w-4 mx-auto" />}
                    </td>
                  </tr>
                  {expandedOrderId === order.orderId && (
                    <tr key={`${order.orderId}-detail`} className="border-t bg-muted/10">
                      <td colSpan={5} className="px-4 py-2">
                        <OrderItemsDetail orderId={order.orderId} />
                      </td>
                    </tr>
                  )}
                </>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function OrderItemsDetail({ orderId }: { orderId: string }) {
  const { data: order, isLoading } = useOrderDetail(orderId);

  if (isLoading) return <div className="py-2"><Skeleton className="h-16 w-full" /></div>;
  if (!order?.items) return null;

  return (
    <table className="w-full text-xs">
      <thead>
        <tr className="text-muted-foreground">
          <th className="text-left py-1 font-normal">{GEO.customerName}</th>
          <th className="text-left py-1 font-normal">{GEO.board}</th>
          <th className="text-left py-1 font-normal">{GEO.comment}</th>
        </tr>
      </thead>
      <tbody>
        {order.items.map((item) => (
          <OrderItemRow key={item.itemId} item={item} orderId={orderId} />
        ))}
      </tbody>
    </table>
  );
}

function OrderItemRow({ item, orderId }: { item: OrderItem; orderId: string }) {
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(item.board ?? '');
  const inputRef = useRef<HTMLInputElement>(null);
  const updateBoard = useUpdateOrderItemBoard();

  const handleEdit = () => {
    setValue(item.board ?? '');
    setEditing(true);
    setTimeout(() => inputRef.current?.focus(), 0);
  };

  const handleSave = () => {
    const trimmed = value.trim();
    if (trimmed !== (item.board ?? '')) {
      updateBoard.mutate({ orderId, itemId: item.itemId, board: trimmed || null });
    }
    setEditing(false);
  };

  const handleCancel = () => {
    setValue(item.board ?? '');
    setEditing(false);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') handleSave();
    if (e.key === 'Escape') handleCancel();
  };

  return (
    <tr className="border-t border-muted/50">
      <td className="py-1.5 pr-4">{item.customerName}</td>
      <td className="py-1.5 pr-4">
        {editing ? (
          <div className="flex items-center gap-1">
            <input
              ref={inputRef}
              value={value}
              onChange={(e) => setValue(e.target.value)}
              onBlur={handleSave}
              onKeyDown={handleKeyDown}
              className="h-6 px-1.5 text-xs rounded border border-input bg-background focus:outline-none focus:ring-1 focus:ring-ring w-28"
            />
            <button onClick={handleSave} className="p-0.5 text-green-600">
              <Check className="h-3 w-3" />
            </button>
            <button onClick={handleCancel} className="p-0.5 text-destructive">
              <X className="h-3 w-3" />
            </button>
          </div>
        ) : (
          <button
            onClick={handleEdit}
            className="flex items-center gap-1 group text-left"
          >
            {item.board ? (
              <span className="text-blue-700 bg-blue-50 px-1 rounded">{item.board}</span>
            ) : (
              <span className="text-muted-foreground/50 italic">{GEO.noBoard}</span>
            )}
            <Pencil className="h-2.5 w-2.5 text-muted-foreground/40 opacity-0 group-hover:opacity-100 transition-opacity" />
          </button>
        )}
      </td>
      <td className="py-1.5 text-muted-foreground">{item.comment}</td>
    </tr>
  );
}
