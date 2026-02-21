'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { Send, Save } from 'lucide-react';
import { GEO } from '@/lib/geo';
import type { SelectedCustomer } from '@/types';

interface OrderActionsProps {
  items: SelectedCustomer[];
  onSend: () => void;
  onSaveDraft: (name: string) => void;
  isSending: boolean;
  isSaving: boolean;
}

export function OrderActions({ items, onSend, onSaveDraft, isSending, isSaving }: OrderActionsProps) {
  const [showSaveDialog, setShowSaveDialog] = useState(false);
  const [draftName, setDraftName] = useState('');

  if (items.length === 0) return null;

  const handleSave = () => {
    if (draftName.trim()) {
      onSaveDraft(draftName.trim());
      setShowSaveDialog(false);
      setDraftName('');
    }
  };

  return (
    <>
      <div className="sticky bottom-[60px] left-0 right-0 flex gap-2 px-4 py-3 border-t bg-background md:bottom-0">
        <Button
          onClick={onSend}
          disabled={isSending || items.length === 0}
          className="flex-1"
        >
          <Send className="h-4 w-4 mr-2" />
          {GEO.send} ({items.length})
        </Button>
        <Button
          variant="secondary"
          onClick={() => setShowSaveDialog(true)}
          disabled={isSaving || items.length === 0}
        >
          <Save className="h-4 w-4 mr-2" />
          {GEO.save}
        </Button>
      </div>

      <Dialog open={showSaveDialog} onOpenChange={setShowSaveDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{GEO.saveDraftName}</DialogTitle>
          </DialogHeader>
          <Input
            value={draftName}
            onChange={(e) => setDraftName(e.target.value)}
            placeholder={GEO.saveDraftName}
            onKeyDown={(e) => e.key === 'Enter' && handleSave()}
          />
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowSaveDialog(false)}>
              {GEO.cancel}
            </Button>
            <Button onClick={handleSave} disabled={!draftName.trim()}>
              {GEO.save}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
