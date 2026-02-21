'use client';

import { AuthGuard } from '@/components/layout/AuthGuard';
import { AppShell } from '@/components/layout/AppShell';
import { GEO } from '@/lib/geo';

export default function AccountantLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard allowedRoles={['ACCOUNTANT', 'ADMIN']}>
      <AppShell title={GEO.reports}>{children}</AppShell>
    </AuthGuard>
  );
}
