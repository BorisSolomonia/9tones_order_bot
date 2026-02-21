'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api, buildApiUrl } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { GEO } from '@/lib/geo';
import { formatDate } from '@/lib/utils';
import { Download } from 'lucide-react';
import type { Order, User } from '@/types';

export default function ReportsPage() {
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [managerId, setManagerId] = useState('');

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
              </tr>
            </thead>
            <tbody>
              {orders?.map((order) => (
                <tr key={order.orderId} className="border-t">
                  <td className="px-4 py-3">{formatDate(order.date)}</td>
                  <td className="px-4 py-3">{order.managerName}</td>
                  <td className="px-4 py-3 text-center">{order.itemCount}</td>
                  <td className="px-4 py-3 text-center">
                    <Badge variant={order.status === 'SENT' ? 'success' : 'destructive'}>
                      {order.status === 'SENT' ? GEO.success : GEO.failed}
                    </Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
