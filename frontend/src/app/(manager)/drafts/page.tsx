'use client';

import { useState, useCallback, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { toast } from 'sonner';
import { useVirtualizer } from '@tanstack/react-virtual';
import { useDrafts, useCreateDraft, useUpdateDraft, useDeleteDraft, useLoadDraft } from '@/hooks/use-drafts';
import { useCustomers } from '@/hooks/use-customers';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { GEO } from '@/lib/geo';
import { formatDateTime } from '@/lib/utils';
import { Trash2, Upload, Plus, ArrowLeft, X, Search, Users } from 'lucide-react';
import type { Draft, DraftItem, Customer, SelectedCustomer } from '@/types';

function compositeKey(customerId: string, board: string | undefined) {
  return `${customerId}|${board ?? ''}`;
}

export default function DraftsPage() {
  const { data: drafts, isLoading } = useDrafts();
  const createDraft = useCreateDraft();
  const updateDraft = useUpdateDraft();
  const deleteDraft = useDeleteDraft();
  const loadDraft = useLoadDraft();
  const router = useRouter();

  // Create dialog
  const [showCreateDialog, setShowCreateDialog] = useState(false);
  const [newDraftName, setNewDraftName] = useState('');

  // Detail view
  const [activeDraft, setActiveDraft] = useState<Draft | null>(null);
  const [draftItems, setDraftItems] = useState<DraftItem[]>([]);
  const [showAddCustomers, setShowAddCustomers] = useState(false);

  const openDraft = (draft: Draft) => {
    setActiveDraft(draft);
    setDraftItems([...draft.items]);
    setShowAddCustomers(false);
  };

  const closeDraft = () => {
    setActiveDraft(null);
    setDraftItems([]);
    setShowAddCustomers(false);
  };

  const handleCreate = async () => {
    const name = newDraftName.trim();
    if (!name) return;
    try {
      const draft = await createDraft.mutateAsync({ name, items: [] });
      toast.success(GEO.draftSaved);
      setShowCreateDialog(false);
      setNewDraftName('');
      openDraft(draft);
    } catch {
      toast.error(GEO.error);
    }
  };

  const handleLoad = async (draftId: string) => {
    try {
      const draft = await loadDraft.mutateAsync(draftId);
      sessionStorage.setItem('draft-items', JSON.stringify(draft.items));
      router.push('/orders');
    } catch {
      toast.error(GEO.error);
    }
  };

  const handleDelete = async (draftId: string) => {
    try {
      await deleteDraft.mutateAsync(draftId);
      toast.success(GEO.delete);
      if (activeDraft?.draftId === draftId) closeDraft();
    } catch {
      toast.error(GEO.error);
    }
  };

  const removeItemFromDraft = useCallback((customerId: string, board: string | undefined) => {
    setDraftItems((prev) =>
      prev.filter(
        (i) => !(i.customerId === customerId && (i.board ?? '') === (board ?? ''))
      )
    );
  }, []);

  const addCustomerToDraft = useCallback((customer: Customer) => {
    setDraftItems((prev) => {
      if (prev.some((i) => i.customerId === customer.customerId && (i.board ?? '') === (customer.board ?? ''))) {
        return prev;
      }
      return [...prev, { customerName: customer.name, customerId: customer.customerId, comment: '', board: customer.board }];
    });
  }, []);

  const saveDraftChanges = async () => {
    if (!activeDraft) return;
    try {
      const updated = await updateDraft.mutateAsync({
        id: activeDraft.draftId,
        name: activeDraft.name,
        items: draftItems.map((i) => ({
          customerName: i.customerName,
          customerId: i.customerId,
          comment: i.comment || '',
          board: i.board ?? undefined,
        })),
      });
      setActiveDraft(updated);
      toast.success(GEO.draftSaved);
    } catch {
      toast.error(GEO.error);
    }
  };

  // Draft detail fullscreen view
  if (activeDraft) {
    return (
      <DraftDetail
        draft={activeDraft}
        items={draftItems}
        showAddCustomers={showAddCustomers}
        onToggleAddCustomers={() => setShowAddCustomers((p) => !p)}
        onRemoveItem={removeItemFromDraft}
        onAddCustomer={addCustomerToDraft}
        onSave={saveDraftChanges}
        onLoad={() => handleLoad(activeDraft.draftId)}
        onDelete={() => handleDelete(activeDraft.draftId)}
        onBack={closeDraft}
        isSaving={updateDraft.isPending}
      />
    );
  }

  return (
    <div className="flex flex-col h-[calc(100vh-60px-53px)]">
      {/* Create button */}
      <div className="px-4 pt-4 pb-2 shrink-0">
        <Button
          onClick={() => setShowCreateDialog(true)}
          className="w-full"
        >
          <Plus className="h-4 w-4 mr-2" />
          {GEO.createDraft}
        </Button>
      </div>

      {/* Drafts list */}
      <div className="flex-1 overflow-y-auto px-4 pb-4 space-y-3">
        {isLoading ? (
          <div className="space-y-2">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-20 w-full" />
            ))}
          </div>
        ) : !drafts || drafts.length === 0 ? (
          <p className="text-center text-muted-foreground py-8">{GEO.noDrafts}</p>
        ) : (
          drafts.map((draft) => (
            <div
              key={draft.draftId}
              className="border rounded-lg p-4 cursor-pointer active:bg-muted/50 transition-colors"
              onClick={() => openDraft(draft)}
            >
              <div className="flex items-start justify-between">
                <div className="flex-1 min-w-0">
                  <p className="font-medium text-sm">{draft.name}</p>
                  <div className="flex items-center gap-2 mt-1">
                    <Badge variant="secondary" className="text-[10px]">
                      <Users className="h-3 w-3 mr-1" />
                      {draft.items.length}
                    </Badge>
                    <span className="text-[10px] text-muted-foreground">
                      {formatDateTime(draft.updatedAt)}
                    </span>
                  </div>
                  <div className="flex flex-wrap gap-1 mt-2">
                    {draft.items.slice(0, 4).map((item, i) => (
                      <Badge key={i} variant="outline" className="text-[10px]">
                        {item.customerName}{item.board ? ` / ${item.board}` : ''}
                      </Badge>
                    ))}
                    {draft.items.length > 4 && (
                      <Badge variant="outline" className="text-[10px]">
                        +{draft.items.length - 4}
                      </Badge>
                    )}
                  </div>
                </div>
                <div className="flex gap-1 shrink-0 ml-2">
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8"
                    onClick={(e) => { e.stopPropagation(); handleLoad(draft.draftId); }}
                  >
                    <Upload className="h-4 w-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8"
                    onClick={(e) => { e.stopPropagation(); handleDelete(draft.draftId); }}
                  >
                    <Trash2 className="h-4 w-4 text-destructive" />
                  </Button>
                </div>
              </div>
            </div>
          ))
        )}
      </div>

      {/* Create draft dialog */}
      <Dialog open={showCreateDialog} onOpenChange={setShowCreateDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{GEO.createDraft}</DialogTitle>
          </DialogHeader>
          <Input
            value={newDraftName}
            onChange={(e) => setNewDraftName(e.target.value)}
            placeholder={GEO.saveDraftName}
            onKeyDown={(e) => e.key === 'Enter' && handleCreate()}
            autoFocus
          />
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowCreateDialog(false)}>
              {GEO.cancel}
            </Button>
            <Button onClick={handleCreate} disabled={!newDraftName.trim() || createDraft.isPending}>
              {GEO.create}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

// ─── Draft Detail View ───────────────────────────────────────────────
interface DraftDetailProps {
  draft: Draft;
  items: DraftItem[];
  showAddCustomers: boolean;
  onToggleAddCustomers: () => void;
  onRemoveItem: (customerId: string, board: string | undefined) => void;
  onAddCustomer: (customer: Customer) => void;
  onSave: () => void;
  onLoad: () => void;
  onDelete: () => void;
  onBack: () => void;
  isSaving: boolean;
}

function DraftDetail({
  draft,
  items,
  showAddCustomers,
  onToggleAddCustomers,
  onRemoveItem,
  onAddCustomer,
  onSave,
  onLoad,
  onDelete,
  onBack,
  isSaving,
}: DraftDetailProps) {
  const itemKeys = new Set(items.map((i) => compositeKey(i.customerId, i.board)));

  return (
    <div className="fixed inset-0 z-50 bg-background flex flex-col">
      {/* Header */}
      <div className="flex items-center gap-3 px-4 py-3 border-b shrink-0">
        <button onClick={onBack} className="p-1 min-h-[44px] flex items-center">
          <ArrowLeft className="h-5 w-5" />
        </button>
        <div className="flex-1 min-w-0">
          <h2 className="text-sm font-medium truncate">{draft.name}</h2>
          <p className="text-[10px] text-muted-foreground">{items.length} {GEO.customers.toLowerCase()}</p>
        </div>
        <button
          onClick={onDelete}
          className="p-2 min-h-[44px] flex items-center text-destructive"
        >
          <Trash2 className="h-4 w-4" />
        </button>
      </div>

      {/* Content area */}
      <div className="flex-1 flex flex-col min-h-0 overflow-hidden">
        {showAddCustomers ? (
          <CustomerSearchPanel
            existingKeys={itemKeys}
            onAddCustomer={onAddCustomer}
            onClose={onToggleAddCustomers}
          />
        ) : (
          /* Draft items list */
          <div className="flex-1 overflow-y-auto">
            {items.length === 0 ? (
              <p className="text-center text-muted-foreground py-8 text-sm">{GEO.emptyDraft}</p>
            ) : (
              items.map((item) => (
                <div
                  key={compositeKey(item.customerId, item.board)}
                  className="flex items-center gap-3 px-4 py-3 border-b"
                >
                  <div className="flex-1 min-w-0">
                    <span className="text-sm truncate">{item.customerName}</span>
                    {item.board && (
                      <span className="ml-2 text-xs text-primary/70">{item.board}</span>
                    )}
                  </div>
                  <button
                    onClick={() => onRemoveItem(item.customerId, item.board)}
                    className="shrink-0 p-2 min-h-[44px] flex items-center"
                  >
                    <X className="h-4 w-4 text-destructive" />
                  </button>
                </div>
              ))
            )}
          </div>
        )}
      </div>

      {/* Bottom actions */}
      <div className="shrink-0 px-4 py-3 border-t bg-background flex flex-col gap-2">
        {!showAddCustomers && (
          <Button
            variant="outline"
            onClick={onToggleAddCustomers}
            className="w-full"
          >
            <Plus className="h-4 w-4 mr-2" />
            {GEO.addCustomers}
          </Button>
        )}
        <div className="flex gap-2">
          <Button onClick={onSave} disabled={isSaving} className="flex-1">
            {GEO.save}
          </Button>
          <Button variant="secondary" onClick={onLoad} className="flex-1">
            <Upload className="h-4 w-4 mr-2" />
            {GEO.loadDraft}
          </Button>
        </div>
      </div>
    </div>
  );
}

// ─── Customer Search Panel (inline in draft detail) ──────────────────
interface CustomerSearchPanelProps {
  existingKeys: Set<string>;
  onAddCustomer: (customer: Customer) => void;
  onClose: () => void;
}

function CustomerSearchPanel({ existingKeys, onAddCustomer, onClose }: CustomerSearchPanelProps) {
  const { customers, isLoading, search, setSearch } = useCustomers('all');
  const parentRef = useRef<HTMLDivElement>(null);

  const virtualizer = useVirtualizer({
    count: customers.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 44,
    overscan: 10,
  });

  return (
    <div className="flex flex-col flex-1 min-h-0">
      {/* Search header with close */}
      <div className="flex items-center gap-2 px-4 py-2 border-b">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder={GEO.search}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
            autoFocus
          />
        </div>
        <button onClick={onClose} className="p-2 min-h-[44px] flex items-center">
          <X className="h-5 w-5" />
        </button>
      </div>

      {/* Customer list */}
      {isLoading ? (
        <div className="flex flex-col gap-2 p-4">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-10 w-full" />
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
              const key = compositeKey(customer.customerId, customer.board);
              const alreadyAdded = existingKeys.has(key);
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
                  <div
                    role="button"
                    tabIndex={alreadyAdded ? -1 : 0}
                    aria-disabled={alreadyAdded}
                    onClick={() => !alreadyAdded && onAddCustomer(customer)}
                    onKeyDown={(e) => {
                      if (alreadyAdded) return;
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        onAddCustomer(customer);
                      }
                    }}
                    className={`flex items-center gap-3 px-4 w-full h-full border-b text-left transition-colors ${
                      alreadyAdded ? 'opacity-40' : 'active:bg-muted/50'
                    }`}
                  >
                    <Checkbox checked={alreadyAdded} disabled className="h-4 w-4 shrink-0" />
                    <div className="flex flex-col min-w-0">
                      <span className="text-[11px] truncate">{customer.name}</span>
                      {customer.board && (
                        <span className="text-[9px] text-primary/70 truncate">{customer.board}</span>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
