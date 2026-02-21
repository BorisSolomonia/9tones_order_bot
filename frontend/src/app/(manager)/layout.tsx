'use client';

import { AuthGuard } from '@/components/layout/AuthGuard';
import { AppShell } from '@/components/layout/AppShell';
import { BottomNav } from '@/components/layout/BottomNav';

export default function ManagerLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard allowedRoles={['MANAGER']}>
      <AppShell>
        <div className="pb-[60px]">{children}</div>
        <BottomNav />
      </AppShell>
    </AuthGuard>
  );
}
