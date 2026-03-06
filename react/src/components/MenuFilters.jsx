import { useState } from 'react';

function MenuFilters({ searchTerm = '', onSearch, selectedType, onTypeChange }) {
  const [searchOpen, setSearchOpen] = useState(false);
  const showSearchInput = searchOpen || Boolean(searchTerm);

  return (
    <section className="filters-panel">
      <div className="search-shell">
        <button
          type="button"
          className="search-toggle"
          aria-label="Search dishes"
          onClick={() => setSearchOpen((prev) => !prev)}
        >
          🔍
        </button>
        {showSearchInput && (
          <input
            type="text"
            value={searchTerm}
            onChange={(event) => onSearch(event.target.value)}
            placeholder="Search dish by name"
            className="search-input"
          />
        )}
      </div>

      <div className="type-filter" role="group" aria-label="Dietary filter">
        <button
          type="button"
          className={selectedType === 'all' ? 'active' : ''}
          onClick={() => onTypeChange('all')}
        >
          All
        </button>
        <button
          type="button"
          className={selectedType === 'veg' ? 'active' : ''}
          onClick={() => onTypeChange('veg')}
        >
          Veg
        </button>
        <button
          type="button"
          className={selectedType === 'non-veg' ? 'active' : ''}
          onClick={() => onTypeChange('non-veg')}
        >
          Non-Veg
        </button>
      </div>
    </section>
  );
}

export default MenuFilters;
