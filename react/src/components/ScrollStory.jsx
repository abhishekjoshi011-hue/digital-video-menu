import { useEffect, useMemo, useRef, useState } from 'react';

const FALLBACK_COPY = [
  {
    title: 'Enter The Experience',
    text: 'A warm table, curated ambiance, and dishes crafted to slow down your evening.'
  },
  {
    title: 'Seasonal Highlights',
    text: 'Fresh ingredients chosen daily, balanced flavors, and plated with precision.'
  },
  {
    title: 'Signature Course',
    text: 'Our most-loved selections designed for sharing and conversation.'
  },
  {
    title: 'Finish With Indulgence',
    text: 'A refined final note from the kitchen to complete your meal journey.'
  }
];

function ScrollStory({ items = [], loading = false }) {
  const [activeIndex, setActiveIndex] = useState(0);
  const [parallaxShift, setParallaxShift] = useState(0);
  const [pointerTilt, setPointerTilt] = useState({ x: 0, y: 0 });
  const stepRefs = useRef([]);
  const sectionRef = useRef(null);
  const mediaRef = useRef(null);

  const frames = useMemo(() => {
    const withImages = (items || [])
      .filter((item) => item?.image || item?.imageUrl)
      .slice(0, 4);

    const tones = ['tone-gold', 'tone-emerald', 'tone-rose', 'tone-noir'];
    return FALLBACK_COPY.map((copy, index) => {
      const match = withImages[index];
      return {
        id: `story-${index}`,
        title: match?.name || copy.title,
        text: match?.description || copy.text,
        image: match?.image || match?.imageUrl || '',
        category: match?.category || 'Chef Story',
        tone: tones[index % tones.length]
      };
    });
  }, [items]);

  useEffect(() => {
    if (!stepRefs.current.length) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          const idx = Number(entry.target.getAttribute('data-step-index') || 0);
          setActiveIndex(idx);
        });
      },
      { rootMargin: '-30% 0px -45% 0px', threshold: 0.2 }
    );

    stepRefs.current.forEach((node) => node && observer.observe(node));
    return () => observer.disconnect();
  }, [frames.length]);

  useEffect(() => {
    let frameId = null;
    const onScroll = () => {
      if (frameId != null) return;
      frameId = window.requestAnimationFrame(() => {
        frameId = null;
        const section = sectionRef.current;
        if (!section) return;
        const rect = section.getBoundingClientRect();
        const viewMid = window.innerHeight * 0.5;
        const delta = viewMid - (rect.top + rect.height * 0.5);
        const bounded = Math.max(-32, Math.min(32, delta * 0.08));
        setParallaxShift(bounded);
      });
    };

    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll);
    return () => {
      if (frameId != null) {
        window.cancelAnimationFrame(frameId);
      }
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('resize', onScroll);
    };
  }, []);

  const handlePointerMove = (event) => {
    const node = mediaRef.current;
    if (!node) return;
    const rect = node.getBoundingClientRect();
    const relX = (event.clientX - rect.left) / rect.width;
    const relY = (event.clientY - rect.top) / rect.height;
    const clamp = (value, min, max) => Math.max(min, Math.min(max, value));
    const tiltY = clamp((relX - 0.5) * 10, -5, 5);
    const tiltX = clamp((0.5 - relY) * 8, -4, 4);
    setPointerTilt({ x: tiltX, y: tiltY });
  };

  const handlePointerLeave = () => {
    setPointerTilt({ x: 0, y: 0 });
  };

  if (loading) {
    return null;
  }

  return (
    <section
      className="scroll-story"
      aria-label="Menu journey"
      ref={sectionRef}
      style={{
        '--story-shift': `${parallaxShift.toFixed(2)}px`,
        '--tilt-x': `${(pointerTilt.x + parallaxShift * 0.04).toFixed(2)}deg`,
        '--tilt-y': `${(pointerTilt.y - parallaxShift * 0.06).toFixed(2)}deg`,
        '--glare-x': `${50 + pointerTilt.y * 5}%`,
        '--glare-y': `${45 - pointerTilt.x * 6}%`
      }}
    >
      <div className="scroll-story-heading">
        <p>Scroll Narrative</p>
        <h2>Taste Journey</h2>
      </div>

      <div className="scroll-story-layout">
        <aside
          className="scroll-story-media"
          ref={mediaRef}
          onMouseMove={handlePointerMove}
          onMouseLeave={handlePointerLeave}
        >
          {frames.map((frame, index) => (
            <figure
              key={frame.id}
              className={`scroll-media-frame ${frame.tone} ${activeIndex === index ? 'active' : ''}`}
              aria-hidden={activeIndex !== index}
            >
              {frame.image ? (
                <img src={frame.image} alt={frame.title} />
              ) : (
                <div className="scroll-media-fallback">
                  <span>{frame.category}</span>
                </div>
              )}
              <figcaption>
                <small>{frame.category}</small>
                <strong>{frame.title}</strong>
              </figcaption>
            </figure>
          ))}
        </aside>

        <div className="scroll-story-steps">
          {frames.map((frame, index) => (
            <article
              key={frame.id}
              data-step-index={index}
              ref={(node) => {
                stepRefs.current[index] = node;
              }}
              className={`scroll-step ${activeIndex === index ? 'active' : ''}`}
            >
              <small>{String(index + 1).padStart(2, '0')}</small>
              <h3>{frame.title}</h3>
              <p>{frame.text}</p>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

export default ScrollStory;
