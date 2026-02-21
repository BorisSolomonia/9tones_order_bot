export const GEO = {
  // Navigation
  orders: 'შეკვეთები',
  history: 'ისტორია',
  drafts: 'დრაფტები',
  reports: 'ანგარიშები',
  users: 'მომხმარებლები',
  customers: 'კლიენტები',
  sync: 'სინქრონიზაცია',

  // Auth
  login: 'შესვლა',
  logout: 'გამოსვლა',
  username: 'მომხმარებელი',
  password: 'პაროლი',
  loginButton: 'შესვლა',
  loginError: 'არასწორი მომხმარებელი ან პაროლი',

  // Orders
  send: 'გაგზავნა',
  save: 'შენახვა',
  selected: 'არჩეული',
  comment: 'კომენტარი',
  search: 'ძიება...',
  myCustomers: 'ჩემი კლიენტები',
  allCustomers: 'ყველა კლიენტი',
  noCustomersFound: 'კლიენტი ვერ მოიძებნა',
  orderSent: 'შეკვეთა გაგზავნილია',
  orderFailed: 'შეკვეთის გაგზავნა ვერ მოხერხდა',
  telegramFailed: 'შეკვეთა შენახულია, მაგრამ ტელეგრამში ვერ გაიგზავნა',
  draftSaved: 'დრაფტი შენახულია',
  selectedList: 'არჩეული სია',
  back: 'უკან',

  // Drafts
  loadDraft: 'ჩატვირთვა',
  deleteDraft: 'წაშლა',
  draftSuggest: (day: string) => `${day}ს სია მზადაა - ჩატვირთვა?`,
  saveDraftName: 'დრაფტის სახელი',
  noDrafts: 'დრაფტები არ არის',
  createDraft: 'ახალი სია',
  addCustomers: 'კლიენტების დამატება',
  draftCustomers: 'კლიენტები სიაში',
  emptyDraft: 'სია ცარიელია — დაამატეთ კლიენტები',
  editDraft: 'სიის რედაქტირება',

  // History
  date: 'თარიღი',
  manager: 'მენეჯერი',
  status: 'სტატუსი',
  customerCount: 'კლიენტების რაოდენობა',
  telegram: 'ტელეგრამი',
  noOrders: 'შეკვეთები არ არის',
  export: 'ექსპორტი',

  // Admin
  addUser: 'მომხმარებლის დამატება',
  editUser: 'რედაქტირება',
  displayName: 'სახელი',
  role: 'როლი',
  active: 'აქტიური',
  inactive: 'არააქტიური',
  addCustomer: 'კლიენტის დამატება',
  customerName: 'კლიენტის სახელი',
  tin: 'საიდ. კოდი',

  // Sync
  triggerSync: 'სინქრონიზაციის დაწყება',
  syncStatus: 'სტატუსი',
  syncHistory: 'ისტორია',
  fullSync: 'სრული სინქრონიზაცია',
  fullSyncDesc: 'RS.GE კლიენტების ჩამოტვირთვა ბოლო 2 თვის მონაცემებით',
  dailySync: 'დღიური',
  running: 'მიმდინარეობს',
  success: 'წარმატება',
  failed: 'შეცდომა',
  customersFound: 'ნაპოვნი',
  customersAdded: 'დამატებული',

  // General
  loading: 'იტვირთება...',
  error: 'შეცდომა',
  confirm: 'დადასტურება',
  cancel: 'გაუქმება',
  delete: 'წაშლა',
  edit: 'რედაქტირება',
  create: 'შექმნა',
  total: 'სულ',
} as const;

export const WEEKDAYS_GEO = [
  'ორშაბათი', 'სამშაბათი', 'ოთხშაბათი', 'ხუთშაბათი', 'პარასკევი', 'შაბათი', 'კვირა',
] as const;
