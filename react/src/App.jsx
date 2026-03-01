import { useEffect, useMemo, useRef, useState } from 'react';
import axios from 'axios';
import MenuHeader from './components/MenuHeader';
import MenuFilters from './components/MenuFilters';
import MenuList from './components/MenuList';
import CartSummary from './components/CartSummary';
import DishDetailsModal from './components/DishDetailsModal';
import ScrollStory from './components/ScrollStory';
import './App.css';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
const TENANT_ID = import.meta.env.VITE_TENANT_ID || 'demo-restaurant';

const normalizeDishType = (type, dish = null) => {
  const fallbackRaw =
    type ??
    dish?.type ??
    dish?.dishType ??
    dish?.vegNonVeg ??
    (dish?.isVeg === true ? 'veg' : dish?.isVeg === false ? 'non-veg' : '');
  const value = String(fallbackRaw || '').toLowerCase().replace(/[^a-z]/g, '');
  if (!value) return 'non-veg';
  if (value === 'veg' || value === 'vegetarian' || value === 'vegan' || value.startsWith('veg')) return 'veg';
  if (value === 'nonveg' || value === 'nonvegetarian' || value.includes('nonveg')) return 'non-veg';
  return 'non-veg';
};

const DISH_KIND_RULES = [
  { label: 'Noodles', terms: ['noodle', 'ramen', 'chow mein', 'spaghetti', 'pasta'] },
  { label: 'Dumplings', terms: ['dumpling', 'momo', 'gyoza', 'wonton'] },
  { label: 'Rice', terms: ['rice', 'biryani', 'risotto', 'fried rice'] },
  { label: 'Breads', terms: ['bread', 'naan', 'roti', 'kulcha', 'bun', 'baguette'] },
  { label: 'Grill', terms: ['grill', 'grilled', 'bbq', 'kebab', 'tandoori'] },
  { label: 'Curries', terms: ['curry', 'masala', 'korma', 'gravy'] },
  { label: 'Bowls', terms: ['bowl'] },
  { label: 'Burgers', terms: ['burger', 'slider'] },
  { label: 'Pizzas', terms: ['pizza'] },
  { label: 'Desserts', terms: ['dessert', 'cake', 'brownie', 'ice cream', 'sweet'] },
  { label: 'Beverages', terms: ['beverage', 'drink', 'coffee', 'tea', 'juice', 'soda', 'mocktail'] }
];

const getDishKind = (item) => {
  const haystack = `${item?.name || ''} ${item?.description || ''} ${item?.category || ''}`.toLowerCase();
  const match = DISH_KIND_RULES.find((rule) => rule.terms.some((term) => haystack.includes(term)));
  return match?.label || 'Chef Specials';
};

const mapCartToOrderItems = (items) =>
  items.map((item) => ({
    dishId: item.id,
    dishName: item.name,
    quantity: item.quantity,
    unitPrice: Number(item.price || 0),
    selectedAddOns: []
  }));

function App() {
  const [adminView, setAdminView] = useState(window.location.hash === '#/admin');

  useEffect(() => {
    const onHashChange = () => setAdminView(window.location.hash === '#/admin');
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  if (adminView) {
    return <AdminConsole apiBaseUrl={API_BASE_URL} />;
  }

  return <CustomerMenu apiBaseUrl={API_BASE_URL} defaultTenantId={TENANT_ID} />;
}

function CustomerMenu({ apiBaseUrl, defaultTenantId }) {
  const queryParams = useMemo(() => new URLSearchParams(window.location.search), []);
  const qrToken = queryParams.get('t') || '';
  const tenantId = queryParams.get('tenant') || defaultTenantId;
  const backgroundVideoUrl =
    import.meta.env.VITE_MENU_BG_VIDEO_URL ||
    'https://videos.pexels.com/video-files/2600043/2600043-hd_1920_1080_30fps.mp4';

  const [menuItems, setMenuItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedCategory, setSelectedCategory] = useState('All');
  const [selectedType, setSelectedType] = useState('all');
  const [selectedDishKind, setSelectedDishKind] = useState('All dishes');
  const [searchTerm, setSearchTerm] = useState('');
  const [showStory, setShowStory] = useState(false);
  const [cartItems, setCartItems] = useState([]);
  const [selectedDish, setSelectedDish] = useState(null);
  const [orderState, setOrderState] = useState({ submitting: false, message: '' });
  const retryInFlightRef = useRef(false);
  const queueStorageKey = `pending-orders:${qrToken || 'no-token'}`;

  useEffect(() => {
    const fetchMenu = async () => {
      try {
        const url = qrToken
          ? `${apiBaseUrl}/api/public/menu?t=${encodeURIComponent(qrToken)}`
          : `${apiBaseUrl}/api/public/${tenantId}/menu`;
        const response = await axios.get(url);
        setMenuItems(response.data);
      } catch (error) {
        console.error('Failed to fetch menu', error);
      } finally {
        setLoading(false);
      }
    };
    fetchMenu();
  }, [apiBaseUrl, qrToken, tenantId]);

  const handleAddToCart = (dish) => {
    setCartItems((prevCart) => {
      const existingItem = prevCart.find((item) => item.id === dish.id);
      if (existingItem) {
        return prevCart.map((item) =>
          item.id === dish.id ? { ...item, quantity: item.quantity + 1 } : item
        );
      }
      return [...prevCart, { ...dish, quantity: 1 }];
    });
  };

  const handleIncrement = (id) => {
    setCartItems((prevCart) =>
      prevCart.map((item) =>
        item.id === id ? { ...item, quantity: item.quantity + 1 } : item
      )
    );
  };

  const handleDecrement = (id) => {
    setCartItems((prevCart) => {
      return prevCart
        .map((item) => (item.id === id ? { ...item, quantity: item.quantity - 1 } : item))
        .filter((item) => item.quantity > 0);
    });
  };

  const placeOrder = async () => {
    if (cartItems.length === 0 || orderState.submitting) {
      return;
    }

    if (!qrToken) {
      setOrderState({ submitting: false, message: 'Missing signed QR token in URL. Please scan restaurant QR again.' });
      return;
    }

    const createIdempotencyKey = () => {
      if (window.crypto && typeof window.crypto.randomUUID === 'function') {
        return window.crypto.randomUUID();
      }
      return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
    };

    const readQueue = () => {
      try {
        return JSON.parse(localStorage.getItem(queueStorageKey) || '[]');
      } catch {
        return [];
      }
    };

    const writeQueue = (records) => {
      localStorage.setItem(queueStorageKey, JSON.stringify(records));
    };

    const queueOrder = (payload) => {
      const existing = readQueue();
      existing.push({
        payload,
        createdAt: new Date().toISOString(),
        attempts: 0
      });
      writeQueue(existing);
    };

    const payload = {
      qrToken,
      items: mapCartToOrderItems(cartItems),
      idempotencyKey: createIdempotencyKey()
    };

    setOrderState({ submitting: true, message: '' });
    try {
      await axios.post(`${apiBaseUrl}/api/public/orders`, payload);

      setCartItems([]);
      setOrderState({ submitting: false, message: 'Order placed successfully.' });
    } catch (error) {
      const isNetworkFailure = !error?.response;
      const isRetryableServerFailure = Number(error?.response?.status || 0) >= 500;

      if (isNetworkFailure || isRetryableServerFailure) {
        queueOrder(payload);
        setCartItems([]);
        setOrderState({
          submitting: false,
          message: 'Connection issue. Order saved locally and will auto-retry when network is back.'
        });
        return;
      }

      const apiMessage = error?.response?.data?.message;
      setOrderState({ submitting: false, message: apiMessage || 'Failed to place order.' });
    }
  };

  useEffect(() => {
    const readQueue = () => {
      try {
        return JSON.parse(localStorage.getItem(queueStorageKey) || '[]');
      } catch {
        return [];
      }
    };

    const writeQueue = (records) => {
      localStorage.setItem(queueStorageKey, JSON.stringify(records));
    };

    const processPendingQueue = async () => {
      if (retryInFlightRef.current) return;
      if (!navigator.onLine) return;

      const queue = readQueue();
      if (!queue.length) return;

      retryInFlightRef.current = true;
      try {
        const remaining = [];
        for (const record of queue) {
          try {
            await axios.post(`${apiBaseUrl}/api/public/orders`, record.payload);
          } catch (error) {
            const isRetryable = !error?.response || Number(error?.response?.status || 0) >= 500;
            if (isRetryable) {
              remaining.push({
                ...record,
                attempts: Number(record.attempts || 0) + 1
              });
            }
          }
        }

        writeQueue(remaining);
        if (remaining.length === 0) {
          setOrderState((prev) => ({
            ...prev,
            message: prev.message || 'All pending orders synced successfully.'
          }));
        }
      } finally {
        retryInFlightRef.current = false;
      }
    };

    const onOnline = () => {
      processPendingQueue();
    };

    processPendingQueue();
    window.addEventListener('online', onOnline);
    const intervalId = window.setInterval(processPendingQueue, 15000);
    return () => {
      window.removeEventListener('online', onOnline);
      window.clearInterval(intervalId);
    };
  }, [apiBaseUrl, queueStorageKey]);

  const baseCategories = ['All', 'Starters', 'Main Course', 'Desserts', 'Beverages'];
  const categories = [
    ...new Set([
      ...baseCategories,
      ...menuItems.map((item) => item.category).filter((c) => c)
    ])
  ];

  const dishKindCounts = menuItems.reduce((acc, item) => {
    const kind = getDishKind(item);
    acc[kind] = (acc[kind] || 0) + 1;
    return acc;
  }, {});

  const dishKindOptions = [
    { label: 'All dishes', count: menuItems.length },
    ...DISH_KIND_RULES.map((rule) => ({
      label: rule.label,
      count: dishKindCounts[rule.label] || 0
    })),
    { label: 'Chef Specials', count: dishKindCounts['Chef Specials'] || 0 }
  ];

  const filteredItems = menuItems.filter((item) => {
    const matchesCategory = selectedCategory === 'All' || item.category === selectedCategory;
    const itemType = normalizeDishType(item.type, item);
    const matchesType = selectedType === 'all' || itemType === selectedType;
    const matchesDishKind = selectedDishKind === 'All dishes' || getDishKind(item) === selectedDishKind;
    const matchesSearch = item.name.toLowerCase().includes(searchTerm.toLowerCase());
    return matchesCategory && matchesType && matchesDishKind && matchesSearch;
  });
  const featuredItems = useMemo(
    () => menuItems.filter((item) => item?.image || item?.imageUrl).slice(0, 3),
    [menuItems]
  );

  const totalAmount = cartItems.reduce((sum, item) => sum + item.price * item.quantity, 0);

  return (
    <div className="menu-experience">
      <div className="menu-page">
        <section className="top-stage">
          <MenuHeader backgroundVideoUrl={backgroundVideoUrl} />
          <aside className="brand-story" aria-label="Restaurant story">
            <p className="story-kicker">House Signature</p>
            <h2>Timeless Craft, Modern Table</h2>
            <p>
              Seasonal ingredients, precise plating, and elevated service. Browse the collection and compose your
              perfect course.
            </p>
            <div className="story-tags">
              <span>Chef Curated</span>
              <span>Fresh Daily</span>
              <span>Table Service</span>
            </div>
          </aside>
        </section>

        <section className="menu-quick-actions" aria-label="Quick actions">
          <button
            type="button"
            className="story-toggle-btn"
            onClick={() => setShowStory((prev) => !prev)}
            aria-expanded={showStory}
            aria-controls="chef-story-section"
          >
            {showStory ? 'Hide Chef Story' : 'Explore Chef Story'}
          </button>
          <a className="jump-menu-btn" href="#menu-selection">Go To Menu</a>
        </section>

        {featuredItems.length > 0 && (
          <section className="chef-highlights" aria-label="Chef recommends">
            <div className="chef-highlights-head">
              <h3>Maison Selection</h3>
              <p>Curated highlights from the kitchen</p>
            </div>
            <div className="chef-highlights-grid">
              {featuredItems.map((item) => (
                <article key={item.id || item.name} className="chef-highlight-card">
                  <img src={item.image || item.imageUrl} alt={item.name} />
                  <div>
                    <h4>{item.name}</h4>
                    <small>{item.category || 'Signature'}</small>
                    <p>${Number(item.price || 0).toFixed(2)}</p>
                  </div>
                  <button type="button" onClick={() => handleAddToCart(item)}>Add</button>
                </article>
              ))}
            </div>
          </section>
        )}

        {showStory && (
          <div id="chef-story-section">
            <ScrollStory items={menuItems} loading={loading} />
          </div>
        )}

        <MenuFilters
          searchTerm={searchTerm}
          onSearch={setSearchTerm}
          categories={categories}
          selectedCategory={selectedCategory}
          onCategoryChange={setSelectedCategory}
          selectedType={selectedType}
          onTypeChange={setSelectedType}
        />

        <div className="content-layout">
          <aside className="dish-kind-panel">
            <h3>Popular Types</h3>
            <div className="dish-kind-list">
              {dishKindOptions.map((option) => (
                <button
                  key={option.label}
                  type="button"
                  className={`dish-kind-btn ${selectedDishKind === option.label ? 'active' : ''}`}
                  onClick={() => setSelectedDishKind(option.label)}
                >
                  <span>{option.label}</span>
                  <small>{option.count}</small>
                </button>
              ))}
            </div>
          </aside>

          <section className="menu-content" id="menu-selection">
            <div className="menu-content-header">
              <h2>Menu Selection</h2>
              <p>
                Showing <strong>{filteredItems.length}</strong> of <strong>{menuItems.length}</strong> dishes
              </p>
            </div>
            {loading ? (
              <div className="loading-state">
                <h2>Fetching delicious menu...</h2>
              </div>
            ) : (
              <MenuList
                items={filteredItems}
                onAddToCart={handleAddToCart}
                onOpenDishDetails={setSelectedDish}
              />
            )}
          </section>

        <CartSummary
            cartItems={cartItems}
            totalAmount={totalAmount}
            onIncrement={handleIncrement}
            onDecrement={handleDecrement}
            onPlaceOrder={placeOrder}
            orderSubmitting={orderState.submitting}
            orderMessage={orderState.message}
          />
        </div>
      </div>

      <a className="floating-skip-menu" href="#menu-selection">Menu</a>

      <DishDetailsModal dish={selectedDish} onClose={() => setSelectedDish(null)} />
    </div>
  );
}

function AdminConsole({ apiBaseUrl }) {
  const [loginForm, setLoginForm] = useState({ username: '', password: '' });
  const [registerForm, setRegisterForm] = useState({ username: '', password: '', tenantId: '' });
  const [auth, setAuth] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem('admin_auth') || 'null');
    } catch {
      return null;
    }
  });
  const [activeTab, setActiveTab] = useState('login');
  const [authError, setAuthError] = useState('');
  const [orders, setOrders] = useState([]);
  const [ordersLoading, setOrdersLoading] = useState(false);
  const [tableFilter, setTableFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [orderSearch, setOrderSearch] = useState('');
  const [lastUpdatedAt, setLastUpdatedAt] = useState(null);
  const [activeSection, setActiveSection] = useState('orders');
  const [qrTableNumber, setQrTableNumber] = useState('1');
  const [qrMenuUrl, setQrMenuUrl] = useState('');
  const [qrToken, setQrToken] = useState('');
  const [qrMessage, setQrMessage] = useState('');
  const [qrRecords, setQrRecords] = useState([]);

  const authHeaders = auth?.token ? { Authorization: `Bearer ${auth.token}` } : {};
  const statusOptions = ['PLACED', 'PREPARING', 'READY', 'SERVED', 'CANCELLED'];
  const getApiErrorMessage = (error, fallback) => {
    const status = Number(error?.response?.status || 0);
    const backendMessage = error?.response?.data?.message;

    if (backendMessage) return backendMessage;
    if (status === 401 || status === 403) {
      return `${fallback} (session expired or insufficient role). Please login again with an ADMIN/MANAGER/OWNER account.`;
    }
    if (status >= 500) {
      return `${fallback} (server error ${status}). Check backend logs.`;
    }
    if (!error?.response) {
      return `${fallback} (network error). Verify backend is reachable at ${apiBaseUrl}.`;
    }
    return `${fallback} (HTTP ${status}).`;
  };

  const fetchOrders = async (tableNumber) => {
    if (!auth?.token) return;
    setOrdersLoading(true);
    setAuthError('');
    try {
      const endpoint = tableNumber
        ? `${apiBaseUrl}/api/admin/orders/table/${tableNumber}`
        : `${apiBaseUrl}/api/admin/orders`;
      const response = await axios.get(endpoint, { headers: authHeaders });
      setOrders(response.data || []);
      setLastUpdatedAt(new Date());
    } catch (error) {
      setAuthError(error?.response?.data?.message || 'Unable to fetch orders.');
    } finally {
      setOrdersLoading(false);
    }
  };

  const fetchQrRecords = async () => {
    if (!auth?.token) return;
    try {
      const response = await axios.get(`${apiBaseUrl}/api/admin/qr/tables`, { headers: authHeaders });
      setQrRecords(response.data || []);
    } catch (error) {
      setQrMessage(getApiErrorMessage(error, 'Unable to load QR list.'));
    }
  };

  useEffect(() => {
    if (!auth?.token) return;
    fetchOrders(tableFilter);
    fetchQrRecords();
    const timer = setInterval(() => fetchOrders(tableFilter), 15000);
    return () => clearInterval(timer);
  }, [auth?.token, tableFilter]);

  const login = async (event) => {
    event.preventDefault();
    setAuthError('');
    try {
      const response = await axios.post(`${apiBaseUrl}/api/auth/login`, loginForm);
      setAuth(response.data);
      localStorage.setItem('admin_auth', JSON.stringify(response.data));
      setLoginForm({ username: '', password: '' });
    } catch (error) {
      setAuthError(error?.response?.data?.message || 'Login failed.');
    }
  };

  const registerAdmin = async (event) => {
    event.preventDefault();
    setAuthError('');
    try {
      const response = await axios.post(`${apiBaseUrl}/api/auth/register-admin`, registerForm);
      setAuth(response.data);
      localStorage.setItem('admin_auth', JSON.stringify(response.data));
      setRegisterForm({ username: '', password: '', tenantId: '' });
    } catch (error) {
      setAuthError(error?.response?.data?.message || 'Registration failed.');
    }
  };

  const logout = () => {
    localStorage.removeItem('admin_auth');
    setAuth(null);
    setOrders([]);
    setTableFilter('');
    setStatusFilter('ALL');
    setOrderSearch('');
    setQrMenuUrl('');
    setQrToken('');
    setQrMessage('');
    setQrRecords([]);
    setActiveSection('orders');
  };

  const updateOrderStatus = async (orderId, status) => {
    try {
      await axios.patch(
        `${apiBaseUrl}/api/admin/orders/${orderId}/status`,
        { status },
        { headers: authHeaders }
      );
      fetchOrders(tableFilter);
    } catch (error) {
      setAuthError(error?.response?.data?.message || 'Status update failed.');
    }
  };

  const generateTableQr = async () => {
    const parsed = Number(qrTableNumber);
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setQrMessage('Enter a valid table number.');
      return;
    }

    try {
      setQrMessage('');
      const response = await axios.get(`${apiBaseUrl}/api/admin/qr/tables/${parsed}`, { headers: authHeaders });
      setQrMenuUrl(response.data?.menuUrl || '');
      setQrToken(response.data?.token || '');
      setQrMessage(`QR URL ready for table ${parsed}.`);
      fetchQrRecords();
    } catch (error) {
      setQrMessage(getApiErrorMessage(error, 'Unable to generate QR URL'));
    }
  };

  const regenerateTableQr = async () => {
    const parsed = Number(qrTableNumber);
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setQrMessage('Enter a valid table number.');
      return;
    }

    try {
      const response = await axios.post(`${apiBaseUrl}/api/admin/qr/tables/${parsed}/regenerate`, {}, { headers: authHeaders });
      setQrMenuUrl(response.data?.menuUrl || '');
      setQrToken(response.data?.token || '');
      setQrMessage(`QR URL regenerated for table ${parsed}.`);
      fetchQrRecords();
    } catch (error) {
      setQrMessage(getApiErrorMessage(error, 'Unable to regenerate QR URL'));
    }
  };

  const revokeTableQr = async (tableNumber) => {
    const confirmed = window.confirm(`Deactivate QR for table ${tableNumber}?`);
    if (!confirmed) return;
    try {
      await axios.delete(`${apiBaseUrl}/api/admin/qr/tables/${tableNumber}`, { headers: authHeaders });
      setQrMessage(`QR removed for table ${tableNumber}.`);
      if (Number(qrTableNumber) === tableNumber) {
        setQrMenuUrl('');
        setQrToken('');
      }
      fetchQrRecords();
    } catch (error) {
      setQrMessage(getApiErrorMessage(error, 'Unable to remove QR'));
    }
  };

  const copyText = async (value, successMessage) => {
    if (!value) return;
    try {
      await navigator.clipboard.writeText(value);
      setQrMessage(successMessage);
    } catch {
      setQrMessage('Copy failed. Please copy manually.');
    }
  };

  const filteredOrders = useMemo(() => {
    const q = orderSearch.trim().toLowerCase();
    return (orders || []).filter((order) => {
      const matchesStatus = statusFilter === 'ALL' || order.status === statusFilter;
      if (!matchesStatus) return false;
      if (!q) return true;
      const inId = String(order.id || '').toLowerCase().includes(q);
      const inTable = String(order.tableNumber || '').includes(q);
      const inItems = (order.items || []).some((item) =>
        String(item.dishName || '').toLowerCase().includes(q)
      );
      return inId || inTable || inItems;
    });
  }, [orders, statusFilter, orderSearch]);

  const summary = useMemo(() => {
    const base = { PLACED: 0, PREPARING: 0, READY: 0, SERVED: 0, CANCELLED: 0 };
    let revenue = 0;
    const activeTables = new Set();
    (orders || []).forEach((order) => {
      const status = order?.status;
      if (base[status] != null) base[status] += 1;
      if (order?.tableNumber != null && status !== 'SERVED' && status !== 'CANCELLED') {
        activeTables.add(order.tableNumber);
      }
      (order.items || []).forEach((item) => {
        revenue += Number(item.unitPrice || 0) * Number(item.quantity || 0);
      });
    });
    return {
      ...base,
      totalOrders: (orders || []).length,
      activeTables: activeTables.size,
      revenue
    };
  }, [orders]);

  const todaysSalesByHour = useMemo(() => {
    const hours = Array.from({ length: 24 }, (_, hour) => ({ hour, amount: 0 }));
    const now = new Date();
    const y = now.getFullYear();
    const m = now.getMonth();
    const d = now.getDate();

    (orders || []).forEach((order) => {
      if (!order?.createdAt || order?.status === 'CANCELLED') return;
      const placedAt = new Date(order.createdAt);
      if (placedAt.getFullYear() !== y || placedAt.getMonth() !== m || placedAt.getDate() !== d) return;

      const lineTotal = (order.items || []).reduce(
        (sum, item) => sum + Number(item.unitPrice || 0) * Number(item.quantity || 0),
        0
      );
      hours[placedAt.getHours()].amount += lineTotal;
    });

    return hours;
  }, [orders]);

  const salesLine = useMemo(() => {
    const width = 480;
    const height = 190;
    const paddingX = 18;
    const paddingY = 20;
    const max = Math.max(...todaysSalesByHour.map((item) => item.amount), 1);
    const stepX = (width - paddingX * 2) / 23;

    const points = todaysSalesByHour.map((item, idx) => {
      const x = paddingX + idx * stepX;
      const y = height - paddingY - (item.amount / max) * (height - paddingY * 2);
      return `${x},${y}`;
    }).join(' ');

    return { width, height, paddingX, paddingY, points };
  }, [todaysSalesByHour]);

  const todaysTopDishes = useMemo(() => {
    const now = new Date();
    const y = now.getFullYear();
    const m = now.getMonth();
    const d = now.getDate();
    const totals = new Map();

    (orders || []).forEach((order) => {
      if (!order?.createdAt || order?.status === 'CANCELLED') return;
      const placedAt = new Date(order.createdAt);
      if (placedAt.getFullYear() !== y || placedAt.getMonth() !== m || placedAt.getDate() !== d) return;

      (order.items || []).forEach((item) => {
        const key = item.dishName || 'Unnamed Dish';
        const qty = Number(item.quantity || 0);
        totals.set(key, (totals.get(key) || 0) + qty);
      });
    });

    return Array.from(totals.entries())
      .map(([name, qty]) => ({ name, qty }))
      .sort((a, b) => b.qty - a.qty)
      .slice(0, 5);
  }, [orders]);

  if (!auth?.token) {
    return (
      <div className="admin-page">
        <div className="admin-auth-card">
          <div className="admin-auth-tabs">
            <button type="button" className={activeTab === 'login' ? 'active' : ''} onClick={() => setActiveTab('login')}>
              Admin Login
            </button>
            <button type="button" className={activeTab === 'register' ? 'active' : ''} onClick={() => setActiveTab('register')}>
              Register Restaurant
            </button>
          </div>

          {activeTab === 'login' ? (
            <form onSubmit={login} className="admin-form">
              <h2>Restaurant Admin</h2>
              <input type="text" placeholder="Username" value={loginForm.username} onChange={(event) => setLoginForm((prev) => ({ ...prev, username: event.target.value }))} required />
              <input type="password" placeholder="Password" value={loginForm.password} onChange={(event) => setLoginForm((prev) => ({ ...prev, password: event.target.value }))} required />
              <button type="submit">Login</button>
            </form>
          ) : (
            <form onSubmit={registerAdmin} className="admin-form">
              <h2>Create Admin</h2>
              <input type="text" placeholder="Restaurant Tenant ID" value={registerForm.tenantId} onChange={(event) => setRegisterForm((prev) => ({ ...prev, tenantId: event.target.value }))} required />
              <input type="text" placeholder="Username" value={registerForm.username} onChange={(event) => setRegisterForm((prev) => ({ ...prev, username: event.target.value }))} required />
              <input type="password" placeholder="Password (min 8 chars)" value={registerForm.password} onChange={(event) => setRegisterForm((prev) => ({ ...prev, password: event.target.value }))} required minLength={8} />
              <button type="submit">Register + Login</button>
            </form>
          )}

          {authError && <p className="admin-error">{authError}</p>}
          <p className="admin-help">Open customer menu with hash removed. Open admin with <code>#/admin</code>.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="admin-page">
      <div className="admin-topbar">
        <div>
          <h2>Orders Dashboard</h2>
          <p>Tenant: {auth.tenantId} | Admin: {auth.username}</p>
          <p className="admin-last-updated">Last sync: {lastUpdatedAt ? lastUpdatedAt.toLocaleTimeString() : 'Not synced yet'}</p>
        </div>
        <div className="admin-topbar-actions">
          <input type="number" min="1" placeholder="Filter table #" value={tableFilter} onChange={(event) => setTableFilter(event.target.value)} />
          <button type="button" onClick={() => fetchOrders(tableFilter)}>Refresh</button>
          <button type="button" onClick={() => { setTableFilter(''); fetchOrders(); }}>Reset</button>
          <button type="button" onClick={logout}>Logout</button>
        </div>
      </div>

      {authError && <p className="admin-error">{authError}</p>}

      <section className="admin-section-tabs">
        <button type="button" className={activeSection === 'orders' ? 'active' : ''} onClick={() => setActiveSection('orders')}>
          Orders
        </button>
        <button type="button" className={activeSection === 'tables' ? 'active' : ''} onClick={() => setActiveSection('tables')}>
          Table Management
        </button>
        <button type="button" className={activeSection === 'stats' ? 'active' : ''} onClick={() => setActiveSection('stats')}>
          Sales Statistics
        </button>
      </section>

      {activeSection === 'orders' && (
        <>
          <section className="admin-controls">
            <h3>Order Filters</h3>
            <div className="admin-status-filter">
              <button type="button" className={statusFilter === 'ALL' ? 'active' : ''} onClick={() => setStatusFilter('ALL')}>ALL</button>
              {statusOptions.map((status) => (
                <button key={status} type="button" className={statusFilter === status ? 'active' : ''} onClick={() => setStatusFilter(status)}>{status}</button>
              ))}
            </div>
            <button type="button" className="admin-clear-filters" onClick={() => { setStatusFilter('ALL'); setOrderSearch(''); }}>
              Clear Filters
            </button>
            <input type="text" className="admin-order-search" placeholder="Search order id, table, item" value={orderSearch} onChange={(event) => setOrderSearch(event.target.value)} />
          </section>
          {ordersLoading ? (
            <div className="admin-loading">Loading orders...</div>
          ) : filteredOrders.length === 0 ? (
            <div className="admin-empty">No orders found.</div>
          ) : (
            <section className="admin-orders-grid">
              {filteredOrders.map((order) => (
                <article key={order.id} className="admin-order-card">
                  <header>
                    <strong>Table {order.tableNumber}</strong>
                    <span>{order.status}</span>
                  </header>
                  <p>Order ID: {order.id}</p>
                  <p>Placed: {order.createdAt ? new Date(order.createdAt).toLocaleString() : '-'}</p>
                  <ul>
                    {(order.items || []).map((item, index) => (
                      <li key={`${item.dishId || item.dishName}-${index}`}>
                        {item.quantity} x {item.dishName} (${Number(item.unitPrice || 0).toFixed(2)})
                      </li>
                    ))}
                  </ul>
                  <div className="admin-status-actions">
                    {['PLACED', 'PREPARING', 'READY', 'SERVED', 'CANCELLED'].map((status) => (
                      <button key={status} type="button" className={order.status === status ? 'active' : ''} onClick={() => updateOrderStatus(order.id, status)}>
                        {status}
                      </button>
                    ))}
                  </div>
                </article>
              ))}
            </section>
          )}
        </>
      )}

      {activeSection === 'tables' && (
        <section className="admin-section-card">
          <section className="admin-qr-panel">
            <h3>Table QR Management</h3>
            <div className="admin-qr-controls">
              <input type="number" min="1" value={qrTableNumber} onChange={(event) => setQrTableNumber(event.target.value)} placeholder="Table number" />
              <button type="button" onClick={generateTableQr}>Generate QR URL</button>
              <button type="button" onClick={regenerateTableQr}>Regenerate</button>
              <button type="button" onClick={() => copyText(qrMenuUrl, 'QR URL copied.')} disabled={!qrMenuUrl}>Copy URL</button>
            </div>
            {qrMenuUrl && <p className="admin-qr-url">{qrMenuUrl}</p>}
            {qrToken && <p className="admin-qr-token">Token: {qrToken.slice(0, 28)}...</p>}
            {qrMessage && <p className="admin-qr-message">{qrMessage}</p>}
            {qrRecords.length > 0 && (
              <div className="admin-qr-table-list">
                {qrRecords.map((row) => (
                  <div key={`${row.tableNumber}-${row.updatedAt || row.createdAt}`} className={`admin-qr-row ${row.active ? '' : 'inactive'}`}>
                    <div className="admin-qr-row-main">
                      <strong>Table {row.tableNumber}</strong>
                      <span>{row.active ? 'Active' : 'Inactive'}</span>
                      <small>Expires: {row.expiresAt ? new Date(row.expiresAt).toLocaleDateString() : '-'}</small>
                    </div>
                    <div className="admin-qr-row-actions">
                      <button type="button" onClick={() => copyText(row.menuUrl, `Table ${row.tableNumber} URL copied.`)} disabled={!row.active}>Copy</button>
                      <button type="button" onClick={() => setQrTableNumber(String(row.tableNumber))}>Select</button>
                      <button type="button" onClick={() => revokeTableQr(row.tableNumber)} disabled={!row.active}>Remove</button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>
        </section>
      )}

      {activeSection === 'stats' && (
        <>
          <section className="admin-kpi-grid">
            <article className="admin-kpi-card"><p>New Orders</p><strong>{summary.PLACED}</strong></article>
            <article className="admin-kpi-card"><p>Kitchen Queue</p><strong>{summary.PREPARING}</strong></article>
            <article className="admin-kpi-card"><p>Ready to Serve</p><strong>{summary.READY}</strong></article>
            <article className="admin-kpi-card"><p>Active Tables</p><strong>{summary.activeTables}</strong></article>
            <article className="admin-kpi-card"><p>Total Orders</p><strong>{summary.totalOrders}</strong></article>
            <article className="admin-kpi-card"><p>Gross Sales</p><strong>${summary.revenue.toFixed(2)}</strong></article>
          </section>
          <section className="admin-insights-grid">
            <article className="admin-chart-card">
              <header>
                <h3>Today's Sales by Time</h3>
                <small>{new Date().toLocaleDateString()}</small>
              </header>
              <div className="line-chart-wrap">
                <svg viewBox={`0 0 ${salesLine.width} ${salesLine.height}`} className="line-chart" role="img" aria-label="Sales line chart by hour">
                  <polyline className="line-chart-grid" points={`${salesLine.paddingX},${salesLine.height - salesLine.paddingY} ${salesLine.width - salesLine.paddingX},${salesLine.height - salesLine.paddingY}`} />
                  <polyline className="line-chart-path" points={salesLine.points} />
                </svg>
                <div className="line-chart-labels">
                  <span>00</span>
                  <span>06</span>
                  <span>12</span>
                  <span>18</span>
                  <span>23</span>
                </div>
              </div>
            </article>
            <article className="admin-top-dishes-card">
              <header><h3>Top Dishes Today</h3></header>
              {todaysTopDishes.length === 0 ? (
                <p>No dish sales today yet.</p>
              ) : (
                <ul>
                  {todaysTopDishes.map((dish) => (
                    <li key={dish.name}><span>{dish.name}</span><strong>{dish.qty}</strong></li>
                  ))}
                </ul>
              )}
            </article>
          </section>
          {ordersLoading ? (
            <div className="admin-loading">Loading orders...</div>
          ) : filteredOrders.length === 0 ? (
            <div className="admin-empty">No orders yet for today's stats.</div>
          ) : (
            <p className="admin-stat-footnote">Based on currently loaded orders.</p>
          )}
        </>
      )}
    </div>
  );
}

export default App;
