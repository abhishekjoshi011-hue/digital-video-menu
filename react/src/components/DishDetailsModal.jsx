import { useState } from 'react';

const getYoutubeEmbedUrl = (videoUrl) => {
  if (!videoUrl) return null;

  try {
    const url = new URL(videoUrl);

    if (url.hostname.includes('youtu.be')) {
      const id = url.pathname.replace('/', '');
      return id
        ? `https://www.youtube.com/embed/${id}?autoplay=1&mute=1&playsinline=1&rel=0`
        : null;
    }

    if (url.hostname.includes('youtube.com')) {
      const id = url.searchParams.get('v');
      return id
        ? `https://www.youtube.com/embed/${id}?autoplay=1&mute=1&playsinline=1&rel=0`
        : null;
    }
  } catch {
    return null;
  }

  return null;
};

const extractAddOns = (dish) => {
  const raw = dish?.addOns ?? dish?.addons ?? dish?.addOnOptions ?? dish?.addonOptions;
  if (!raw) return [];

  if (Array.isArray(raw)) {
    return raw.map((item) => (typeof item === 'string' ? { name: item } : item)).filter((item) => item?.name);
  }

  if (typeof raw === 'string') {
    return raw.split(',').map((item) => item.trim()).filter(Boolean).map((item) => ({ name: item }));
  }

  return [];
};

function DishDetailsModal({ dish, onClose }) {
  const [selectedAddOns, setSelectedAddOns] = useState([]);

  if (!dish) return null;

  const embedUrl = getYoutubeEmbedUrl(dish.videoUrl);
  const imageSrc = dish.image || dish.imageUrl;
  const addOns = extractAddOns(dish);

  const toggleAddon = (name) => {
    setSelectedAddOns((prev) =>
      prev.includes(name) ? prev.filter((item) => item !== name) : [...prev, name]
    );
  };

  return (
    <div className="dish-modal-backdrop" role="presentation" onClick={onClose}>
      <div className="dish-modal" role="dialog" aria-modal="true" aria-label={`${dish.name} details`} onClick={(event) => event.stopPropagation()}>
        <button type="button" className="dish-modal-close" onClick={onClose} aria-label="Close details">
          ×
        </button>

        <h2>{dish.name}</h2>

        <div className="dish-modal-video">
          {embedUrl ? (
            <iframe
              src={embedUrl}
              title={`${dish.name} video`}
              loading="lazy"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
              allowFullScreen
            />
          ) : dish.videoUrl ? (
            <video src={dish.videoUrl} autoPlay muted loop playsInline controls />
          ) : imageSrc ? (
            <img src={imageSrc} alt={dish.name} className="dish-modal-image" />
          ) : (
            <div className="dish-video-empty">No video available for this dish.</div>
          )}
        </div>

        <div className="dish-modal-section">
          <h3>Description</h3>
          <p>{dish.description || 'No description available.'}</p>
        </div>

        <div className="dish-modal-section">
          <h3>Add-ons</h3>
          {addOns.length === 0 ? (
            <p className="dish-addons-empty">No add-ons listed for this dish yet.</p>
          ) : (
            <ul className="dish-addons-list">
              {addOns.map((addon) => {
                const label = addon.price != null ? `${addon.name} (+$${Number(addon.price).toFixed(2)})` : addon.name;
                return (
                  <li key={addon.name}>
                    <label>
                      <input
                        type="checkbox"
                        checked={selectedAddOns.includes(addon.name)}
                        onChange={() => toggleAddon(addon.name)}
                      />
                      <span>{label}</span>
                    </label>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}

export default DishDetailsModal;
