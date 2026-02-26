'use client';

import { useRef } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import { Search } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { CustomerItem } from './CustomerItem';
import { GEO } from '@/lib/geo';
import type { Customer, SelectedCustomer } from '@/types';

interface SearchPanelProps {
  customers: Customer[];
  isLoading: boolean;
  search: string;
  onSearchChange: (value: string) => void;
  tab: 'all' | 'my';
  onTabChange: (tab: 'all' | 'my') => void;
  selectedItems: SelectedCustomer[];
  myCustomerIds: Set<string>;
  onToggleCustomer: (customer: Customer) => void;
  onToggleMyCustomer: (customer: Customer) => void;
  onCommentChange: (customerId: string, board: string | undefined, comment: string) => void;
}

export function SearchPanel({
  customers,
  isLoading,
  search,
  onSearchChange,
  tab,
  onTabChange,
  selectedItems,
  myCustomerIds,
  onToggleCustomer,
  onToggleMyCustomer,
  onCommentChange,
}: SearchPanelProps) {
  const parentRef = useRef<HTMLDivElement>(null);

  const selectedMap = new Map(
    selectedItems
      .filter((i) => i.customerId)
      .map((i) => [`${i.customerId}|${i.board ?? ''}`, i])
  );

  const virtualizer = useVirtualizer({
    count: customers.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 44,
    overscan: 10,
  });

  return (
    <div className="flex flex-col flex-1 min-h-0">
      {/* Tabs */}
      <div className="flex border-b">
        <button
          className={`flex-1 py-3 text-sm font-medium min-h-[44px] transition-colors ${
            tab === 'my' ? 'text-primary border-b-2 border-primary' : 'text-muted-foreground'
          }`}
          onClick={() => onTabChange('my')}
        >
          {GEO.myCustomers}
        </button>
        <button
          className={`flex-1 py-3 text-sm font-medium min-h-[44px] transition-colors ${
            tab === 'all' ? 'text-primary border-b-2 border-primary' : 'text-muted-foreground'
          }`}
          onClick={() => onTabChange('all')}
        >
          {GEO.allCustomers}
        </button>
      </div>

      {/* Search */}
      <div className="px-4 py-2 border-b">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder={GEO.search}
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
            className="pl-9"
          />
        </div>
        <div className="mt-1 flex items-center justify-end gap-2 text-[10px] text-muted-foreground">
          <span>{GEO.total}: {customers.length}</span>
          <span>{GEO.selected}: {selectedItems.length}</span>
        </div>
      </div>

      {/* Customer list */}
      {isLoading ? (
        <div className="flex flex-col gap-2 p-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="h-14 w-full" />
          ))}
        </div>
      ) : customers.length === 0 ? (
        <div className="flex items-center justify-center p-8 text-muted-foreground text-sm">
          {GEO.noCustomersFound}
        </div>
      ) : (
        <div ref={parentRef} className="flex-1 overflow-y-auto">
          <div
            style={{
              height: `${virtualizer.getTotalSize()}px`,
              width: '100%',
              position: 'relative',
            }}
          >
            {virtualizer.getVirtualItems().map((virtualRow) => {
              const customer = customers[virtualRow.index];
              const key = `${customer.customerId}|${customer.board ?? ''}`;
              const selected = selectedMap.get(key);
              return (
                <div
                  key={key}
                  style={{
                    position: 'absolute',
                    top: 0,
                    left: 0,
                    width: '100%',
                    height: `${virtualRow.size}px`,
                    transform: `translateY(${virtualRow.start}px)`,
                  }}
                >
                  <CustomerItem
                    customer={customer}
                    isSelected={!!selected}
                    isMy={myCustomerIds.has(customer.customerId)}
                    comment={selected?.comment ?? ''}
                    onToggle={() => onToggleCustomer(customer)}
                    onToggleMy={() => onToggleMyCustomer(customer)}
                    onCommentChange={(comment) => onCommentChange(customer.customerId, customer.board, comment)}
                  />
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
