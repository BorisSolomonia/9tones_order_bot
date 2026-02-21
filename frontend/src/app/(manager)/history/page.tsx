'use client';

import { useState } from 'react';
import { useOrders, useOrderDetail } from '@/hooks/use-orders';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { GEO } from '@/lib/geo';
import { formatDate } from '@/lib/utils';
import { ChevronDown, ChevronUp } from 'lucide-react';

export default function HistoryPage() {
  const [date, setDate] = useState('');
  const { data: orders, isLoading } = useOrders(date || undefined);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  return (
    <div className="p-4 space-y-4">
      <div>
        <input
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          className="w-full h-11 px-3 rounded-md border border-input bg-background text-sm"
        />
      </div>

      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-16 w-full" />
          ))}
        </div>
      ) : !orders || orders.length === 0 ? (
        <p className="text-center text-muted-foreground py-8">{GEO.noOrders}</p>
      ) : (
        <div className="space-y-2">
          {orders.map((order) => (
            <div key={order.orderId} className="border rounded-lg">
              <button
                className="w-full flex items-center justify-between p-4 min-h-[44px]"
                onClick={() => setExpandedId(expandedId === order.orderId ? null : order.orderId)}
              >
                <div className="text-left">
                  <p className="text-sm font-medium">{formatDate(order.date)}</p>
                  <p className="text-xs text-muted-foreground">
                    {order.itemCount} {GEO.customers}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <Badge variant={order.status === 'SENT' ? 'success' : 'destructive'}>
                    {order.status === 'SENT' ? GEO.success : GEO.failed}
                  </Badge>
                  {expandedId === order.orderId ? (
                    <ChevronUp className="h-4 w-4" />
                  ) : (
                    <ChevronDown className="h-4 w-4" />
                  )}
                </div>
              </button>

              {expandedId === order.orderId && (
                <OrderDetail orderId={order.orderId} />
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function OrderDetail({ orderId }: { orderId: string }) {
  const { data: order, isLoading } = useOrderDetail(orderId);

  if (isLoading) return <div className="p-4"><Skeleton className="h-20 w-full" /></div>;
  if (!order?.items) return null;

  return (
    <div className="border-t px-4 pb-4">
      {order.items.map((item, i) => (
        <div key={item.itemId} className="py-2 border-b last:border-b-0">
          <p className="text-sm">
            {i + 1}. {item.customerName}
            {item.comment && <span className="text-muted-foreground"> - {item.comment}</span>}
          </p>
        </div>
      ))}
    </div>
  );
}
