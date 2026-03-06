import { useEffect, useMemo, useRef, useState } from 'react';

const IMAGE_DISPLAY_MS = 5000;
const VIDEO_FALLBACK_MS = 5000;

const looksLikeUrl = (value) =>
  typeof value === 'string' &&
  /^(https?:)?\/\//i.test(value.trim());

const fromObject = (obj) => {
  if (!obj || typeof obj !== 'object') return [];
  const direct = [
    obj.url,
    obj.src,
    obj.image,
    obj.imageUrl,
    obj.image_url,
    obj.video,
    obj.videoUrl,
    obj.video_url,
    obj.link
  ];
  return direct
    .map((item) => (typeof item === 'string' ? item.trim() : ''))
    .filter(looksLikeUrl);
};

const toList = (value) => {
  if (!value) return [];
  if (Array.isArray(value)) {
    return value
      .flatMap((item) => {
        if (typeof item === 'string') return item;
        if (Array.isArray(item)) return toList(item);
        if (item && typeof item === 'object') {
          return [
            ...fromObject(item),
            ...toList(item.images),
            ...toList(item.imageUrls),
            ...toList(item.image_urls),
            ...toList(item.videos),
            ...toList(item.videoUrls),
            ...toList(item.video_urls)
          ];
        }
        return '';
      })
      .map((item) => String(item).trim())
      .filter(looksLikeUrl);
  }
  if (typeof value === 'string') {
    const raw = value.trim();
    if (!raw) return [];

    if (raw.startsWith('[') || raw.startsWith('{')) {
      try {
        const parsed = JSON.parse(raw);
        return toList(parsed);
      } catch {
        // Fall through to plain-string handling.
      }
    }

    const looksLikeMultiUrlString =
      raw.includes(',') && (raw.includes('http://') || raw.includes('https://'));

    if (looksLikeMultiUrlString) {
      return raw
        .split(',')
        .map((item) => item.trim().replace(/^['"]|['"]$/g, ''))
        .filter(Boolean);
    }

    const single = raw.replace(/^['"]|['"]$/g, '');
    return looksLikeUrl(single) ? [single] : [];
  }
  if (typeof value === 'object') {
    return fromObject(value);
  }
  return [];
};

const dedupe = (list) => Array.from(new Set(list.filter(Boolean)));

const getYoutubeEmbedUrl = (urlValue) => {
  if (!urlValue) return null;
  try {
    const url = new URL(urlValue);
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

export const extractDishMedia = (dish) => {
  const images = dedupe([
    ...toList(dish?.images),
    ...toList(dish?.image_urls),
    ...toList(dish?.imageUrls),
    ...toList(dish?.gallery_images),
    ...toList(dish?.galleryImages),
    ...toList(dish?.media?.images),
    ...toList(dish?.mediaImages),
    ...toList(dish?.media),
    ...toList(dish?.image_url),
    ...toList(dish?.image),
    ...toList(dish?.imageUrl)
  ]);

  const videos = dedupe([
    ...toList(dish?.videos),
    ...toList(dish?.video_urls),
    ...toList(dish?.videoUrls),
    ...toList(dish?.media?.videos),
    ...toList(dish?.mediaVideos),
    ...toList(dish?.media),
    ...toList(dish?.video_url),
    ...toList(dish?.video),
    ...toList(dish?.videoUrl)
  ]);

  const media = [];

  if (images.length > 0) {
    media.push({ type: 'image', src: images[0] });
  }

  videos.forEach((src) => {
    const embedUrl = getYoutubeEmbedUrl(src);
    if (embedUrl) {
      media.push({ type: 'embed', src, embedUrl });
    } else {
      media.push({ type: 'video', src });
    }
  });

  images.slice(1).forEach((src) => media.push({ type: 'image', src }));

  return dedupe(media.map((item) => `${item.type}:${item.src}`)).map((key) => {
    const [type, ...rest] = key.split(':');
    const src = rest.join(':');
    if (type === 'embed') {
      return { type, src, embedUrl: getYoutubeEmbedUrl(src) };
    }
    return { type, src };
  });
};

function DishMediaCarousel({
  dish,
  className = '',
  onClick,
  showDots = false,
  showArrows = false,
  emptyLabel = 'No media available.'
}) {
  const mediaItems = useMemo(() => extractDishMedia(dish), [dish]);
  const [activeIndex, setActiveIndex] = useState(0);
  const timerRef = useRef(null);
  const touchStartXRef = useRef(0);
  const mouseStartXRef = useRef(0);
  const mouseDownRef = useRef(false);
  const videoRefs = useRef([]);

  const clearTimer = () => {
    if (timerRef.current) {
      window.clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  };

  const goNext = () => {
    setActiveIndex((prev) => (mediaItems.length > 0 ? (prev + 1) % mediaItems.length : 0));
  };

  const goPrev = () => {
    setActiveIndex((prev) =>
      mediaItems.length > 0 ? (prev - 1 + mediaItems.length) % mediaItems.length : 0
    );
  };

  useEffect(() => {
    setActiveIndex(0);
    videoRefs.current = videoRefs.current.slice(0, mediaItems.length);
  }, [mediaItems.length]);

  useEffect(() => {
    clearTimer();

    videoRefs.current.forEach((video, index) => {
      if (video && index !== activeIndex) {
        video.pause();
        video.currentTime = 0;
      }
    });

    if (mediaItems.length <= 1) return undefined;

    const activeItem = mediaItems[activeIndex];
    if (!activeItem) return undefined;

    if (activeItem.type === 'image' || activeItem.type === 'embed') {
      timerRef.current = window.setTimeout(goNext, IMAGE_DISPLAY_MS);
      return clearTimer;
    }

    const activeVideo = videoRefs.current[activeIndex];
    if (activeVideo) {
      activeVideo.currentTime = 0;
      activeVideo.play().catch(() => {});
      const durationMs = Number.isFinite(activeVideo.duration) && activeVideo.duration > 0
        ? Math.max(1000, Math.floor(activeVideo.duration * 1000))
        : VIDEO_FALLBACK_MS;
      timerRef.current = window.setTimeout(goNext, durationMs);
    } else {
      timerRef.current = window.setTimeout(goNext, VIDEO_FALLBACK_MS);
    }

    return clearTimer;
  }, [activeIndex, mediaItems]);

  useEffect(() => clearTimer, []);

  const handleTouchStart = (event) => {
    touchStartXRef.current = event.changedTouches[0]?.clientX || 0;
  };

  const handleTouchEnd = (event) => {
    const endX = event.changedTouches[0]?.clientX || 0;
    const delta = endX - touchStartXRef.current;
    if (Math.abs(delta) < 30) return;
    if (delta < 0) {
      goNext();
    } else {
      goPrev();
    }
  };

  const handleMouseDown = (event) => {
    mouseDownRef.current = true;
    mouseStartXRef.current = event.clientX || 0;
  };

  const handleMouseUp = (event) => {
    if (!mouseDownRef.current) return;
    mouseDownRef.current = false;
    const endX = event.clientX || 0;
    const delta = endX - mouseStartXRef.current;
    if (Math.abs(delta) < 30) return;
    if (delta < 0) goNext();
    else goPrev();
  };

  if (!mediaItems.length) {
    return <div className="dish-media-empty">{emptyLabel}</div>;
  }

  return (
    <div
      className={`dish-media-carousel ${onClick ? 'is-clickable' : ''} ${className}`.trim()}
      onTouchStart={handleTouchStart}
      onTouchEnd={handleTouchEnd}
      onMouseDown={handleMouseDown}
      onMouseUp={handleMouseUp}
      onMouseLeave={() => {
        mouseDownRef.current = false;
      }}
      onClick={onClick}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      onKeyDown={
        onClick
          ? (event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                onClick();
              }
            }
          : undefined
      }
    >
      <div
        className="dish-media-track"
        style={{ transform: `translateX(-${activeIndex * 100}%)` }}
      >
        {mediaItems.map((item, index) => (
          <div key={`${item.type}-${item.src}-${index}`} className="dish-media-slide">
            {item.type === 'image' && (
              <div className="dish-media-frame">
                <img src={item.src} alt="" className="dish-media-bg-image" aria-hidden="true" />
                <img src={item.src} alt={dish?.name || 'Dish'} className="dish-image" />
              </div>
            )}
            {item.type === 'video' && (
              <div className="dish-media-frame">
                <video
                  src={item.src}
                  className="dish-media-bg-video"
                  muted
                  playsInline
                  autoPlay
                  loop
                  preload="metadata"
                  aria-hidden="true"
                />
                <video
                  ref={(el) => {
                    videoRefs.current[index] = el;
                  }}
                  src={item.src}
                  className="dish-video"
                  muted
                  playsInline
                  preload="metadata"
                  onLoadedMetadata={() => {
                    if (index !== activeIndex || mediaItems.length <= 1) return;
                    clearTimer();
                    const video = videoRefs.current[index];
                    const durationMs = Number.isFinite(video?.duration) && video.duration > 0
                      ? Math.max(1000, Math.floor(video.duration * 1000))
                      : VIDEO_FALLBACK_MS;
                    timerRef.current = window.setTimeout(goNext, durationMs);
                  }}
                  onEnded={() => {
                    if (index === activeIndex && mediaItems.length > 1) {
                      goNext();
                    }
                  }}
                />
              </div>
            )}
            {item.type === 'embed' && item.embedUrl && (
              <iframe
                src={item.embedUrl}
                title={`${dish?.name || 'Dish'} video`}
                loading="lazy"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                allowFullScreen
                className="dish-media-embed"
              />
            )}
            {item.type === 'embed' && !item.embedUrl && (
              <div className="dish-media-empty">Invalid video URL</div>
            )}
          </div>
        ))}
      </div>

      {showDots && mediaItems.length > 1 && (
        <div className="dish-media-dots" onClick={(event) => event.stopPropagation()}>
          {mediaItems.map((_, index) => (
            <button
              key={`dot-${index}`}
              type="button"
              className={`dish-media-dot ${index === activeIndex ? 'active' : ''}`}
              onClick={() => setActiveIndex(index)}
              aria-label={`Show media ${index + 1}`}
            />
          ))}
        </div>
      )}

      {showArrows && mediaItems.length > 1 && (
        <>
          <button
            type="button"
            className="dish-media-nav prev"
            onClick={(event) => {
              event.stopPropagation();
              goPrev();
            }}
            aria-label="Show previous media"
          >
            ‹
          </button>
          <button
            type="button"
            className="dish-media-nav next"
            onClick={(event) => {
              event.stopPropagation();
              goNext();
            }}
            aria-label="Show next media"
          >
            ›
          </button>
        </>
      )}
    </div>
  );
}

export default DishMediaCarousel;
