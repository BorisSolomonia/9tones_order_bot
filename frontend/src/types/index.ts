export interface User {
  userId: string;
  username: string;
  displayName: string;
  role: 'MANAGER' | 'ACCOUNTANT' | 'ADMIN';
  active: boolean;
  createdAt: string;
}

export interface Customer {
  customerId: string;
  name: string;
  tin: string;
  frequencyScore: number;
  addedBy: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  board?: string;
}

export interface Order {
  orderId: string;
  managerId: string;
  managerName: string;
  date: string;
  status: 'SENT' | 'FAILED';
  telegramSent: boolean;
  telegramSentAt: string;
  itemCount: number;
  createdAt: string;
  items?: OrderItem[];
}

export interface OrderItem {
  itemId: string;
  orderId: string;
  customerName: string;
  customerId: string;
  comment: string;
  createdAt: string;
  board?: string;
}

export interface Draft {
  draftId: string;
  managerId: string;
  name: string;
  items: DraftItem[];
  createdAt: string;
  updatedAt: string;
}

export interface DraftItem {
  customerName: string;
  customerId: string;
  comment: string;
  board?: string;
}

export interface MyCustomer {
  managerId: string;
  customerName: string;
  customerId: string;
  addedAt: string;
}

export interface SyncState {
  syncId: string;
  syncType: string;
  startDate: string;
  endDate: string;
  status: 'RUNNING' | 'SUCCESS' | 'FAILED';
  customersFound: number;
  customersAdded: number;
  errorMessage: string;
  startedAt: string;
  completedAt: string;
}

export interface SelectedCustomer {
  customerName: string;
  customerId?: string;
  comment: string;
  board?: string;
}
