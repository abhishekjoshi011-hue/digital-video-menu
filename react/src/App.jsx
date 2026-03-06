import { useEffect, useMemo, useRef, useState } from 'react';
import axios from 'axios';
import MenuHeader from './components/MenuHeader';
import MenuFilters from './components/MenuFilters';
import MenuList from './components/MenuList';
import CartSummary from './components/CartSummary';
import DishDetailsModal from './components/DishDetailsModal';
import ScrollStory from './components/ScrollStory';
import DishMediaCarousel from './components/DishMediaCarousel';
import { Component as IconTabs3D } from './components/ui/3d-icon-tabs-1';
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

const mapCartToOrderItems = (items) =>
  items.map((item) => ({
    dishId: item.id,
    dishName: item.name,
    quantity: item.quantity,
    unitPrice: Number(item.price || 0),
    selectedAddOns: []
  }));

const normalizeCategoryKey = (value) => String(value || '').trim().toLowerCase().replace(/\s+/g, ' ');
const CATEGORY_GROUPS = [
  { key: 'food', label: 'Food' },
  { key: 'beverages', label: 'Beverages' },
  { key: 'desserts', label: 'Desserts' }
];

const getCategoryGroupKey = (categoryLabel) => {
  const value = normalizeCategoryKey(categoryLabel);
  if (
    value.includes('beverage') ||
    value.includes('drink') ||
    value.includes('tea') ||
    value.includes('mocktail')
  ) {
    return 'beverages';
  }
  if (
    value.includes('dessert') ||
    value.includes('sweet') ||
    value.includes('ice cream')
  ) {
    return 'desserts';
  }
  return 'food';
};

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

  const [menuItems, setMenuItems] = useState([]);
  const [branding, setBranding] = useState({ tenantId: '', backgroundVideoUrl: '', backgroundImageUrl: '' });
  const [loading, setLoading] = useState(true);
  const [selectedCategoryGroup, setSelectedCategoryGroup] = useState('food');
  const [selectedCategory, setSelectedCategory] = useState('group:food');
  const [selectedType, setSelectedType] = useState('all');
  const [searchTerm, setSearchTerm] = useState('');
  const [showStory, setShowStory] = useState(false);
  const [cartItems, setCartItems] = useState([]);
  const [mobileCartOpen, setMobileCartOpen] = useState(false);
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

  useEffect(() => {
    const fetchBranding = async () => {
      try {
        const url = qrToken
          ? `${apiBaseUrl}/api/public/branding?t=${encodeURIComponent(qrToken)}`
          : `${apiBaseUrl}/api/public/${tenantId}/branding`;
        const response = await axios.get(url);
        setBranding(response.data || { tenantId: '', backgroundVideoUrl: '', backgroundImageUrl: '' });
      } catch (error) {
        console.error('Failed to fetch branding', error);
        setBranding({ tenantId: '', backgroundVideoUrl: '', backgroundImageUrl: '' });
      }
    };
    fetchBranding();
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
    if (window.matchMedia('(max-width: 980px)').matches) {
      setMobileCartOpen(true);
    }
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
      setMobileCartOpen(false);
      setOrderState({ submitting: false, message: 'Order placed successfully.' });
    } catch (error) {
      const isNetworkFailure = !error?.response;
      const isRetryableServerFailure = Number(error?.response?.status || 0) >= 500;

      if (isNetworkFailure || isRetryableServerFailure) {
        queueOrder(payload);
        setCartItems([]);
        setMobileCartOpen(false);
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

  const groupedCategoryOptions = useMemo(() => {
    const grouped = {
      food: [],
      beverages: [],
      desserts: []
    };
    const groupCounts = {
      food: 0,
      beverages: 0,
      desserts: 0
    };

    const countsByCategory = new Map();
    const labelsByCategory = new Map();

    menuItems.forEach((item) => {
      const label = String(item?.category || 'Uncategorized').trim() || 'Uncategorized';
      const key = normalizeCategoryKey(label);
      labelsByCategory.set(key, label);
      countsByCategory.set(key, (countsByCategory.get(key) || 0) + 1);
    });

    Array.from(countsByCategory.entries())
      .map(([key, count]) => ({ key, label: labelsByCategory.get(key) || key, count }))
      .forEach((category) => {
        const group = getCategoryGroupKey(category.label);
        grouped[group].push(category);
        groupCounts[group] += category.count;
      });

    Object.values(grouped).forEach((list) => list.sort((a, b) => a.label.localeCompare(b.label)));

    return { grouped, groupCounts };
  }, [menuItems]);

  const activeCategoryOptions = useMemo(() => {
    const groupLabel = CATEGORY_GROUPS.find((group) => group.key === selectedCategoryGroup)?.label || 'Food';
    return [
      {
        key: `group:${selectedCategoryGroup}`,
        label: `All ${groupLabel}`,
        count: groupedCategoryOptions.groupCounts[selectedCategoryGroup] || 0
      },
      ...(groupedCategoryOptions.grouped[selectedCategoryGroup] || [])
    ];
  }, [groupedCategoryOptions, selectedCategoryGroup]);

  const selectedCategoryLabel = useMemo(() => {
    if (selectedCategory.startsWith('group:')) {
      return 'All';
    }
    return activeCategoryOptions.find((option) => option.key === selectedCategory)?.label || 'All';
  }, [activeCategoryOptions, selectedCategory]);
  const categoryGroupTabs = useMemo(
    () =>
      CATEGORY_GROUPS.map((group) => ({
        id: group.key,
        label: group.label,
        icon: group.key === 'food' ? '🍽️' : group.key === 'beverages' ? '🍹' : '🍨',
        video_url:
          group.key === 'food'
            ? 'https://a0.muscache.com/videos/search-bar-icons/webm/house-selected.webm'
            : group.key === 'beverages'
              ? 'https://a0.muscache.com/videos/search-bar-icons/webm/balloon-selected.webm'
              : 'https://a0.muscache.com/videos/search-bar-icons/webm/consierge-selected.webm',
        initial_render_url:
          group.key === 'food'
            ? 'https://a0.muscache.com/videos/search-bar-icons/webm/house-twirl-selected.webm'
            : group.key === 'beverages'
              ? 'https://a0.muscache.com/videos/search-bar-icons/webm/balloon-twirl.webm'
              : 'https://a0.muscache.com/videos/search-bar-icons/webm/consierge-twirl.webm'
      })),
    []
  );

  const filteredItems = menuItems.filter((item) => {
    const itemCategory = normalizeCategoryKey(item?.category || 'Uncategorized');
    const itemGroup = getCategoryGroupKey(item?.category || 'Uncategorized');
    const matchesCategory = selectedCategory.startsWith('group:')
      ? itemGroup === selectedCategory.replace('group:', '')
      : itemCategory === selectedCategory;
    const itemType = normalizeDishType(item.type, item);
    const matchesType = selectedType === 'all' || itemType === selectedType;
    const matchesSearch = item.name.toLowerCase().includes(searchTerm.toLowerCase());
    return matchesCategory && matchesType && matchesSearch;
  });
  const featuredItems = useMemo(() => {
    const explicit = menuItems.filter((item) => item?.isBestSeller === true || item?.isBestseller === true);
    if (explicit.length > 0) {
      return explicit.slice(0, 3);
    }
    return menuItems.slice(0, 3);
  }, [menuItems]);
  const brandTitle = useMemo(() => {
    const sourceTenant = branding?.tenantId || menuItems.find((item) => item?.tenantId)?.tenantId || tenantId || 'digital-menu';
    return String(sourceTenant)
      .replace(/[-_]+/g, ' ')
      .replace(/\s+/g, ' ')
      .trim()
      .replace(/\b\w/g, (char) => char.toUpperCase());
  }, [branding?.tenantId, menuItems, tenantId]);
  const backgroundVideoUrl = useMemo(
    () => String(branding?.backgroundVideoUrl || '').trim(),
    [branding?.backgroundVideoUrl]
  );
  const backgroundImageUrl = useMemo(
    () => String(branding?.backgroundImageUrl || '').trim(),
    [branding?.backgroundImageUrl]
  );

  const totalAmount = cartItems.reduce((sum, item) => sum + item.price * item.quantity, 0);

  return (
    <div className="menu-experience">
      <div className="menu-page">
        <section className="top-stage">
          <MenuHeader
            backgroundVideoUrl={backgroundVideoUrl}
            backgroundImageUrl={backgroundImageUrl}
            title={brandTitle}
          />
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
          <section className="chef-highlights" aria-label="Our bestsellers">
            <div className="chef-highlights-head">
              <h3>Our Bestsellers</h3>
              <p>Top picks your guests keep reordering</p>
            </div>
            <div className="chef-highlights-grid">
              {featuredItems.map((item) => (
                <article key={item.id || item.name} className="chef-highlight-card">
                  <div className="chef-highlight-left">
                    <div className="chef-highlight-media">
                      <DishMediaCarousel dish={item} emptyLabel="No media" />
                    </div>
                    <strong className="chef-highlight-price">${Number(item.price || 0).toFixed(2)}</strong>
                  </div>
                  <div className="chef-highlight-right">
                    <h4>{item.name}</h4>
                    <small>{item.category || 'Signature'}</small>
                    <button type="button" onClick={() => handleAddToCart(item)}>Add to Cart</button>
                  </div>
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
          selectedType={selectedType}
          onTypeChange={setSelectedType}
        />

        <div className="content-layout">
          <aside className="dish-kind-panel">
            <h3>Categories</h3>
            <IconTabs3D
              tabs={categoryGroupTabs}
              defaultTab={selectedCategoryGroup}
              activeTab={selectedCategoryGroup}
              className="max-w-none"
              onTabChange={(groupKey) => {
                const selectedGroup = String(groupKey);
                setSelectedCategoryGroup(selectedGroup);
                setSelectedCategory(`group:${selectedGroup}`);
              }}
            />
            <p className="category-breadcrumb-path">
              <span>{CATEGORY_GROUPS.find((group) => group.key === selectedCategoryGroup)?.label || 'Food'}</span>
              <span>/</span>
              <strong>{selectedCategoryLabel}</strong>
            </p>
            <div className="dish-kind-list">
              {activeCategoryOptions.map((option) => (
                <button
                  key={option.key}
                  type="button"
                  className={`dish-kind-btn ${selectedCategory === option.key ? 'active' : ''}`}
                  onClick={() => setSelectedCategory(option.key)}
                >
                  <span>{option.label}</span>
                </button>
              ))}
            </div>
          </aside>

          <section className="menu-content" id="menu-selection">
            <div className="menu-content-header">
              <h2>Menu Selection</h2>
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

        <button
          type="button"
          className={`mobile-cart-toggle ${mobileCartOpen ? 'hidden' : ''}`}
          onClick={() => setMobileCartOpen(true)}
        >
          Cart ({cartItems.length})
        </button>

        {mobileCartOpen && (
          <button
            type="button"
            className="mobile-cart-backdrop"
            onClick={() => setMobileCartOpen(false)}
            aria-label="Close cart panel"
          />
        )}

        <section className={`mobile-cart-sheet ${mobileCartOpen ? 'open' : ''}`}>
          <div className="mobile-cart-sheet-head">
            <strong>Your Cart</strong>
            <button type="button" onClick={() => setMobileCartOpen(false)}>Close</button>
          </div>
          <CartSummary
            cartItems={cartItems}
            totalAmount={totalAmount}
            onIncrement={handleIncrement}
            onDecrement={handleDecrement}
            onPlaceOrder={placeOrder}
            orderSubmitting={orderState.submitting}
            orderMessage={orderState.message}
          />
        </section>
      </div>

      <DishDetailsModal dish={selectedDish} onClose={() => setSelectedDish(null)} />
    </div>
  );
}

function AdminConsole({ apiBaseUrl }) {
  const [loginForm, setLoginForm] = useState({ username: '', password: '' });
  const [registerForm, setRegisterForm] = useState({
    username: '',
    password: '',
    tenantId: '',
    backgroundVideoUrl: '',
    backgroundImageUrl: ''
  });
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
  const [qrHistoryRecords, setQrHistoryRecords] = useState([]);
  const [showQrHistory, setShowQrHistory] = useState(false);
  const [statsRange, setStatsRange] = useState('week');

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

  const fetchQrHistoryRecords = async () => {
    if (!auth?.token) return;
    try {
      const response = await axios.get(`${apiBaseUrl}/api/admin/qr/tables/history?limit=120`, { headers: authHeaders });
      setQrHistoryRecords(response.data || []);
    } catch (error) {
      setQrMessage(getApiErrorMessage(error, 'Unable to load QR history.'));
    }
  };

  useEffect(() => {
    if (!auth?.token) return;
    fetchOrders(tableFilter);
    fetchQrRecords();
    fetchQrHistoryRecords();
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
      setRegisterForm({
        username: '',
        password: '',
        tenantId: '',
        backgroundVideoUrl: '',
        backgroundImageUrl: ''
      });
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
    setQrHistoryRecords([]);
    setShowQrHistory(false);
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
      fetchQrHistoryRecords();
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
      fetchQrHistoryRecords();
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
      fetchQrHistoryRecords();
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

  const filteredQrHistory = useMemo(() => {
    const selected = Number(qrTableNumber);
    if (!Number.isInteger(selected) || selected <= 0) {
      return qrHistoryRecords;
    }
    return qrHistoryRecords.filter((row) => Number(row.tableNumber) === selected);
  }, [qrHistoryRecords, qrTableNumber]);

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

  const statsOrders = useMemo(() => {
    const now = new Date();
    const from = new Date(now);
    if (statsRange === 'week') {
      from.setHours(0, 0, 0, 0);
      from.setDate(from.getDate() - 6);
    } else if (statsRange === 'month') {
      from.setDate(1);
      from.setHours(0, 0, 0, 0);
    } else {
      from.setMonth(0, 1);
      from.setHours(0, 0, 0, 0);
    }
    return (orders || []).filter((order) => {
      if (!order?.createdAt || order?.status === 'CANCELLED') return false;
      const placedAt = new Date(order.createdAt);
      return placedAt >= from && placedAt <= now;
    });
  }, [orders, statsRange]);

  const summary = useMemo(() => {
    const base = { PLACED: 0, PREPARING: 0, READY: 0, SERVED: 0, CANCELLED: 0 };
    let revenue = 0;
    const activeTables = new Set();
    (statsOrders || []).forEach((order) => {
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
      totalOrders: (statsOrders || []).length,
      activeTables: activeTables.size,
      revenue
    };
  }, [statsOrders]);

  const salesSeries = useMemo(() => {
    const now = new Date();
    let buckets = [];

    if (statsRange === 'year') {
      buckets = Array.from({ length: 12 }, (_, i) => ({
        key: i,
        label: new Date(now.getFullYear(), i, 1).toLocaleString(undefined, { month: 'short' }),
        amount: 0
      }));
      statsOrders.forEach((order) => {
        const placedAt = new Date(order.createdAt);
        if (placedAt.getFullYear() !== now.getFullYear()) return;
        const total = (order.items || []).reduce((sum, item) => sum + Number(item.unitPrice || 0) * Number(item.quantity || 0), 0);
        buckets[placedAt.getMonth()].amount += total;
      });
      return buckets;
    }

    if (statsRange === 'month') {
      const daysInMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
      buckets = Array.from({ length: daysInMonth }, (_, i) => ({
        key: i + 1,
        label: String(i + 1),
        amount: 0
      }));
      statsOrders.forEach((order) => {
        const placedAt = new Date(order.createdAt);
        if (placedAt.getFullYear() !== now.getFullYear() || placedAt.getMonth() !== now.getMonth()) return;
        const total = (order.items || []).reduce((sum, item) => sum + Number(item.unitPrice || 0) * Number(item.quantity || 0), 0);
        buckets[placedAt.getDate() - 1].amount += total;
      });
      return buckets;
    }

    const start = new Date(now);
    start.setHours(0, 0, 0, 0);
    start.setDate(start.getDate() - 6);
    buckets = Array.from({ length: 7 }, (_, i) => {
      const d = new Date(start);
      d.setDate(start.getDate() + i);
      return {
        key: d.toISOString().slice(0, 10),
        label: d.toLocaleDateString(undefined, { weekday: 'short' }),
        amount: 0
      };
    });
    const byKey = new Map(buckets.map((b, i) => [b.key, i]));
    statsOrders.forEach((order) => {
      const placedAt = new Date(order.createdAt);
      const key = placedAt.toISOString().slice(0, 10);
      const idx = byKey.get(key);
      if (idx == null) return;
      const total = (order.items || []).reduce((sum, item) => sum + Number(item.unitPrice || 0) * Number(item.quantity || 0), 0);
      buckets[idx].amount += total;
    });
    return buckets;
  }, [statsOrders, statsRange]);

  const salesLine = useMemo(() => {
    const width = 480;
    const height = 190;
    const paddingX = 18;
    const paddingY = 20;
    const max = Math.max(...salesSeries.map((item) => item.amount), 1);
    const stepX = (width - paddingX * 2) / Math.max(1, salesSeries.length - 1);

    const points = salesSeries.map((item, idx) => {
      const x = paddingX + idx * stepX;
      const y = height - paddingY - (item.amount / max) * (height - paddingY * 2);
      return `${x},${y}`;
    }).join(' ');

    return { width, height, paddingX, paddingY, points };
  }, [salesSeries]);

  const topDishesForRange = useMemo(() => {
    const totals = new Map();

    (statsOrders || []).forEach((order) => {
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
  }, [statsOrders]);

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
              <input type="url" placeholder="Background Video URL (optional)" value={registerForm.backgroundVideoUrl} onChange={(event) => setRegisterForm((prev) => ({ ...prev, backgroundVideoUrl: event.target.value }))} />
              <input type="url" placeholder="Background Image URL (optional)" value={registerForm.backgroundImageUrl} onChange={(event) => setRegisterForm((prev) => ({ ...prev, backgroundImageUrl: event.target.value }))} />
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
          {activeSection === 'orders' && (
            <>
              <input type="number" min="1" placeholder="Filter table #" value={tableFilter} onChange={(event) => setTableFilter(event.target.value)} />
              <button type="button" onClick={() => fetchOrders(tableFilter)}>Refresh</button>
              <button type="button" onClick={() => { setTableFilter(''); fetchOrders(); }}>Reset</button>
            </>
          )}
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
                <button
                  key={status}
                  type="button"
                  className={statusFilter === status ? 'active' : ''}
                  onClick={() => setStatusFilter(status)}
                >
                  {status}
                </button>
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
                    <div className="admin-order-primary">
                      <strong>Table {order.tableNumber}</strong>
                      <ul className="admin-order-items">
                        {(order.items || []).map((item, index) => (
                          <li key={`${item.dishId || item.dishName}-${index}`}>
                            <span className="qty">{item.quantity}x</span>
                            <span className="name">{item.dishName}</span>
                            <span className="price">${Number(item.unitPrice || 0).toFixed(2)}</span>
                          </li>
                        ))}
                      </ul>
                    </div>
                    <span className="admin-order-status">{order.status}</span>
                  </header>
                  <p className="admin-order-time">Placed: {order.createdAt ? new Date(order.createdAt).toLocaleString() : '-'}</p>
                  <div className="admin-status-actions">
                    {['PLACED', 'PREPARING', 'READY', 'SERVED', 'CANCELLED'].map((status) => (
                      <button
                        key={status}
                        type="button"
                        className={order.status === status ? 'active' : ''}
                        onClick={() => updateOrderStatus(order.id, status)}
                      >
                        {status}
                      </button>
                    ))}
                  </div>
                  <p className="admin-order-id">Order ID: {order.id}</p>
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
                <p className="admin-qr-list-title">Active QR Codes</p>
                {qrRecords.map((row) => (
                  <div key={`${row.tableNumber}-${row.updatedAt || row.createdAt}`} className={`admin-qr-row ${row.active ? '' : 'inactive'}`}>
                    <div className="admin-qr-row-main">
                      <strong>Table {row.tableNumber}</strong>
                      <span>{row.active ? 'Active' : 'Inactive'}</span>
                      <small>Updated: {row.updatedAt ? new Date(row.updatedAt).toLocaleString() : '-'}</small>
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
            {qrHistoryRecords.length > 0 && (
              <div className="admin-qr-history-wrap">
                <button
                  type="button"
                  className="admin-qr-history-toggle"
                  onClick={() => setShowQrHistory((prev) => !prev)}
                >
                  {showQrHistory ? 'Hide QR History' : `Show QR History (${filteredQrHistory.length})`}
                </button>
              </div>
            )}
            {showQrHistory && filteredQrHistory.length > 0 && (
              <div className="admin-qr-table-list">
                <p className="admin-qr-list-title">QR History</p>
                {filteredQrHistory.map((row, index) => (
                  <div key={`${row.tableNumber}-${row.token}-${index}`} className={`admin-qr-row ${row.active ? '' : 'inactive'}`}>
                    <div className="admin-qr-row-main">
                      <strong>Table {row.tableNumber}</strong>
                      <span>{row.active ? 'Active' : 'Inactive'}</span>
                      <small>Updated: {row.updatedAt ? new Date(row.updatedAt).toLocaleString() : '-'}</small>
                    </div>
                    <div className="admin-qr-row-actions">
                      <button type="button" onClick={() => copyText(row.menuUrl, `Table ${row.tableNumber} URL copied.`)}>Copy</button>
                      <button type="button" onClick={() => setQrTableNumber(String(row.tableNumber))}>Select</button>
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
          <section className="admin-stats-range">
            <button type="button" className={statsRange === 'week' ? 'active' : ''} onClick={() => setStatsRange('week')}>Week</button>
            <button type="button" className={statsRange === 'month' ? 'active' : ''} onClick={() => setStatsRange('month')}>Month</button>
            <button type="button" className={statsRange === 'year' ? 'active' : ''} onClick={() => setStatsRange('year')}>Year</button>
          </section>
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
                <h3>Sales Trend</h3>
                <small>{statsRange.charAt(0).toUpperCase() + statsRange.slice(1)} view</small>
              </header>
              <div className="line-chart-wrap">
                <svg viewBox={`0 0 ${salesLine.width} ${salesLine.height}`} className="line-chart" role="img" aria-label="Sales line chart by hour">
                  <polyline className="line-chart-grid" points={`${salesLine.paddingX},${salesLine.height - salesLine.paddingY} ${salesLine.width - salesLine.paddingX},${salesLine.height - salesLine.paddingY}`} />
                  <polyline className="line-chart-path" points={salesLine.points} />
                </svg>
                <div className="line-chart-labels">
                  {salesSeries.length <= 12
                    ? salesSeries.map((item) => <span key={item.key}>{item.label}</span>)
                    : [salesSeries[0], salesSeries[Math.floor(salesSeries.length * 0.25)], salesSeries[Math.floor(salesSeries.length * 0.5)], salesSeries[Math.floor(salesSeries.length * 0.75)], salesSeries[salesSeries.length - 1]]
                      .map((item, idx) => <span key={`${item.key}-${idx}`}>{item.label}</span>)
                  }
                </div>
              </div>
            </article>
            <article className="admin-top-dishes-card">
              <header><h3>Top Dishes ({statsRange})</h3></header>
              {topDishesForRange.length === 0 ? (
                <p>No dish sales in selected range.</p>
              ) : (
                <ul>
                  {topDishesForRange.map((dish) => (
                    <li key={dish.name}><span>{dish.name}</span><strong>{dish.qty}</strong></li>
                  ))}
                </ul>
              )}
            </article>
          </section>
          {ordersLoading ? (
            <div className="admin-loading">Loading orders...</div>
          ) : statsOrders.length === 0 ? (
            <div className="admin-empty">No orders for selected range.</div>
          ) : (
            <p className="admin-stat-footnote">Based on currently loaded orders.</p>
          )}
        </>
      )}

    </div>
  );
}

export default App;
