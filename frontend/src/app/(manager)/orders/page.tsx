'use client';

import { useState, useCallback, useEffect } from 'react';
import { toast } from 'sonner';
import { useQueryClient } from '@tanstack/react-query';
import { SearchPanel } from '@/components/orders/SearchPanel';
import { OrderActions } from '@/components/orders/OrderActions';
import { useCustomers } from '@/hooks/use-customers';
import { useCreateOrder } from '@/hooks/use-orders';
import { useCreateDraft, useDraftSuggestion, useLoadDraft } from '@/hooks/use-drafts';
import { api } from '@/lib/api';
import { GEO } from '@/lib/geo';
import { Badge } from '@/components/ui/badge';
import { X, ArrowLeft } from 'lucide-react';
import type { Customer, SelectedCustomer, MyCustomer } from '@/types';

export default function OrdersPage() {
  const [selectedItems, setSelectedItems] = useState<SelectedCustomer[]>([]);
  const [tab, setTab] = useState<'all' | 'my'>('my');
  const [myCustomerIds, setMyCustomerIds] = useState<Set<string>>(new Set());
  const [suggestDismissed, setSuggestDismissed] = useState(false);
  const [showSelectedOverlay, setShowSelectedOverlay] = useState(false);

  const { customers, isLoading, search, setSearch } = useCustomers(tab);
  const createOrder = useCreateOrder();
  const createDraft = useCreateDraft();
  const { data: suggestedDraft } = useDraftSuggestion();
  const loadDraft = useLoadDraft();
  const queryClient = useQueryClient();

  // Load my customer IDs
  useEffect(() => {
    api.get<MyCustomer[]>('/api/v1/customers/my').then((data) => {
      setMyCustomerIds(new Set(data.map((mc) => mc.customerId)));
    }).catch(() => {});
  }, []);

  // Load draft items from sessionStorage (set by drafts page)
  useEffect(() => {
    const stored = sessionStorage.getItem('draft-items');
    if (stored) {
      try {
        const items = JSON.parse(stored) as SelectedCustomer[];
        if (items.length > 0) {
          setSelectedItems(items.map((i) => ({
            customerName: i.customerName,
            customerId: i.customerId,
            comment: i.comment || '',
          })));
        }
      } catch { /* ignore */ }
      sessionStorage.removeItem('draft-items');
    }
  }, []);

  const toggleCustomer = useCallback((customer: Customer) => {
    setSelectedItems((prev) => {
      const exists = prev.find((i) => i.customerId === customer.customerId);
      if (exists) {
        return prev.filter((i) => i.customerId !== customer.customerId);
      }
      return [...prev, { customerName: customer.name, customerId: customer.customerId, comment: '' }];
    });
  }, []);

  const updateCommentByCustomerId = useCallback((customerId: string, comment: string) => {
    setSelectedItems((prev) => {
      const exists = prev.find((i) => i.customerId === customerId);
      if (exists) {
        return prev.map((item) =>
          item.customerId === customerId ? { ...item, comment } : item
        );
      }
      return prev;
    });
  }, []);

  const toggleMyCustomer = useCallback(async (customer: Customer) => {
    const isMy = myCustomerIds.has(customer.customerId);
    try {
      if (isMy) {
        await api.delete(`/api/v1/customers/my/${customer.customerId}`);
        setMyCustomerIds((prev) => {
          const next = new Set(prev);
          next.delete(customer.customerId);
          return next;
        });
      } else {
        await api.post('/api/v1/customers/my', {
          customerName: customer.name,
          customerId: customer.customerId,
        });
        setMyCustomerIds((prev) => new Set(prev).add(customer.customerId));
      }
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      queryClient.invalidateQueries({ queryKey: ['my-customers'] });
    } catch {
      toast.error(GEO.error);
    }
  }, [myCustomerIds, queryClient]);

  const removeItem = useCallback((customerId: string) => {
    setSelectedItems((prev) => prev.filter((i) => i.customerId !== customerId));
  }, []);

  const handleSend = async () => {
    try {
      const order = await createOrder.mutateAsync({ items: selectedItems, sendTelegram: true });
      if (order && order.telegramSent) {
        toast.success(GEO.orderSent);
      } else {
        toast.error(GEO.telegramFailed);
      }
      setSelectedItems([]);
      setShowSelectedOverlay(false);
    } catch {
      toast.error(GEO.orderFailed);
    }
  };

  const handleSaveDraft = async (name: string) => {
    try {
      await createDraft.mutateAsync({ name, items: selectedItems });
      toast.success(GEO.draftSaved);
    } catch {
      toast.error(GEO.error);
    }
  };

  const handleLoadSuggested = async () => {
    if (!suggestedDraft) return;
    try {
      const draft = await loadDraft.mutateAsync(suggestedDraft.draftId);
      setSelectedItems(
        draft.items.map((i) => ({
          customerName: i.customerName,
          customerId: i.customerId,
          comment: i.comment,
        }))
      );
      setSuggestDismissed(true);
    } catch {
      toast.error(GEO.error);
    }
  };

  return (
    <div className="flex flex-col h-[calc(100vh-60px-53px)]">
      {/* Draft suggestion banner */}
      {suggestedDraft && !suggestDismissed && selectedItems.length === 0 && (
        <div className="flex items-center justify-between gap-2 px-4 py-2 bg-blue-50 border-b shrink-0">
          <span className="text-xs">{GEO.draftSuggest(suggestedDraft.name)}</span>
          <div className="flex gap-2">
            <button
              onClick={handleLoadSuggested}
              className="text-xs font-medium text-primary min-h-[44px] px-2"
            >
              {GEO.loadDraft}
            </button>
            <button
              onClick={() => setSuggestDismissed(true)}
              className="text-xs text-muted-foreground min-h-[44px] px-2"
            >
              &times;
            </button>
          </div>
        </div>
      )}

      {/* Compact vertical selected strip — scrollable, tappable to expand */}
      {selectedItems.length > 0 && (
        <div
          className="shrink-0 border-b bg-card px-3 py-1.5 max-h-24 overflow-y-auto cursor-pointer"
          onClick={() => setShowSelectedOverlay(true)}
        >
          <div className="flex items-center gap-2 mb-1">
            <Badge variant="default" className="text-[10px] px-1.5 py-0">
              {GEO.selected}: {selectedItems.length}
            </Badge>
          </div>
          <div className="flex flex-col gap-0.5">
            {selectedItems.map((item) => (
              <div
                key={item.customerId}
                className="flex items-center justify-between gap-1 py-0.5"
              >
                <span className="text-[10px] text-muted-foreground truncate flex-1">
                  {item.customerName}
                  {item.comment && (
                    <span className="ml-1 text-[9px] opacity-60">— {item.comment}</span>
                  )}
                </span>
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    removeItem(item.customerId!);
                  }}
                  className="shrink-0 p-0.5 hover:text-destructive"
                >
                  <X className="h-2.5 w-2.5" />
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Customer list takes all remaining space */}
      <div className="flex-1 flex flex-col min-h-0">
        <SearchPanel
          customers={customers}
          isLoading={isLoading}
          search={search}
          onSearchChange={setSearch}
          tab={tab}
          onTabChange={setTab}
          selectedItems={selectedItems}
          myCustomerIds={myCustomerIds}
          onToggleCustomer={toggleCustomer}
          onToggleMyCustomer={toggleMyCustomer}
          onCommentChange={updateCommentByCustomerId}
        />
      </div>

      {/* Action buttons pinned to bottom */}
      <OrderActions
        items={selectedItems}
        onSend={handleSend}
        onSaveDraft={handleSaveDraft}
        isSending={createOrder.isPending}
        isSaving={createDraft.isPending}
      />

      {/* Full-screen overlay for selected items */}
      {showSelectedOverlay && (
        <div className="fixed inset-0 z-50 bg-background flex flex-col">
          <div className="flex items-center gap-3 px-4 py-3 border-b shrink-0">
            <button
              onClick={() => setShowSelectedOverlay(false)}
              className="p-1 min-h-[44px] flex items-center"
            >
              <ArrowLeft className="h-5 w-5" />
            </button>
            <h2 className="text-sm font-medium flex-1">
              {GEO.selectedList} ({selectedItems.length})
            </h2>
          </div>
          <div className="flex-1 overflow-y-auto">
            {selectedItems.map((item) => (
              <div
                key={item.customerId}
                className="flex items-center gap-3 px-4 py-3 border-b"
              >
                <div className="flex-1 min-w-0">
                  <span className="text-sm">{item.customerName}</span>
                  {item.comment && (
                    <p className="text-xs text-muted-foreground mt-0.5">{item.comment}</p>
                  )}
                </div>
                <button
                  onClick={() => removeItem(item.customerId!)}
                  className="shrink-0 p-2 min-h-[44px] flex items-center"
                >
                  <X className="h-4 w-4 text-destructive" />
                </button>
              </div>
            ))}
          </div>
          <div className="shrink-0 px-4 py-3 border-t bg-background">
            <button
              onClick={() => setShowSelectedOverlay(false)}
              className="w-full min-h-[44px] text-sm font-medium text-primary"
            >
              {GEO.back}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
