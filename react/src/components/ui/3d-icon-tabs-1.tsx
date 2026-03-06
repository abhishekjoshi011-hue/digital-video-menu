"use client";

import { motion } from "motion/react";
import { useState, useRef, useEffect, type ReactNode } from "react";

import { cn } from "@/lib/utils";

type TabId = string | number;

interface IconTab {
  id: TabId;
  label: string;
  video_url: string;
  initial_render_url: string;
  icon?: ReactNode;
}

interface IconTabsProps {
  className?: string;
  tabs?: IconTab[];
  defaultTab?: TabId;
  activeTab?: TabId;
  onTabChange?: (tabId: TabId) => void;
}

const tabs: IconTab[] = [
  {
    id: "food",
    label: "Food",
    video_url:
      "https://a0.muscache.com/videos/search-bar-icons/webm/house-selected.webm",
    initial_render_url:
      "https://a0.muscache.com/videos/search-bar-icons/webm/house-twirl-selected.webm",
  },
  {
    id: "beverages",
    label: "Beverages",
    video_url:
      "https://a0.muscache.com/videos/search-bar-icons/webm/balloon-selected.webm",
    initial_render_url:
      "https://a0.muscache.com/videos/search-bar-icons/webm/balloon-twirl.webm",
  },
  {
    id: "desserts",
    label: "Desserts",
    video_url:
      "https://a0.muscache.com/videos/search-bar-icons/webm/consierge-selected.webm",
    initial_render_url:
      "https://a0.muscache.com/videos/search-bar-icons/webm/consierge-twirl.webm",
  },
];

function NewBadge({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        "bg-primary px-2 py-1 rounded-t-full rounded-br-full rounded-bl-sm text-xs font-bold text-primary-foreground transition-all duration-200 relative overflow-hidden before:content-[''] before:absolute before:inset-0 before:rounded-[inherit] before:pointer-events-none before:z-[1] before:shadow-[inset_0_0_0_1px_rgba(170,202,255,0.2),inset_0_0_10px_0_rgba(170,202,255,0.3),inset_0_3px_7px_0_rgba(170,202,255,0.4),inset_0_-4px_3px_0_rgba(170,202,255,0.4),0_1px_3px_0_rgba(0,0,0,0.50),0_4px_12px_0_rgba(0,0,0,0.65)]  backdrop-blur-md",
        className
      )}
    >
      <span>NEW</span>
      <span
        className="absolute left-1/2 -translate-x-1/2 opacity-40 z-50 scale-y-[-1] translate-y-2.5"
        style={{
          maskImage: "linear-gradient(to top, white 20%, transparent 50%)",
          WebkitMaskImage:
            "linear-gradient(to top, white 10%, transparent 50%)",
        }}
      >
        NEW
      </span>
    </div>
  );
}

function Component({ className, tabs: inputTabs = tabs, defaultTab, activeTab, onTabChange }: IconTabsProps) {
  const [internalActiveTab, setInternalActiveTab] = useState<TabId>(
    defaultTab ?? inputTabs[0]?.id ?? "food"
  );
  const resolvedActiveTab = activeTab ?? internalActiveTab;

  const videoRefs = useRef<Record<string, HTMLVideoElement | null>>({});

  useEffect(() => {
    if (activeTab !== undefined) {
      setInternalActiveTab(activeTab);
    }
  }, [activeTab]);

  const [tabClicked, setTabClicked] = useState(false);

  const handleTabClick = (newTabId: TabId) => {
    setTabClicked(true);
    setInternalActiveTab(newTabId);

    Object.values(videoRefs.current).forEach((video) => {
      if (video) {
        video.pause();
        video.currentTime = 0;
      }
    });

    const selected = videoRefs.current[String(newTabId)];
    if (selected) {
      selected.currentTime = 0;
      selected.play();
    }

    onTabChange?.(newTabId);
  };

  return (
    <div className="flex flex-col items-center w-full">
      {/* <NewBadge /> */}
      <div className={cn("flex w-full flex-col space-y-1 rounded-2xl", className)}>
        {inputTabs.map((tab, index) => (
          <motion.button
            key={tab.id}
            whileTap={"tapped"}
            whileHover={"hovered"}
            onClick={() => handleTabClick(tab.id)}
            className={cn(
              "relative w-full px-1 py-1 tracking-[0.01em] cursor-pointer text-neutral-600 transition focus-visible:outline-1 focus-visible:ring-1 focus-visible:outline-none flex gap-1 items-center rounded-lg min-h-8",
              resolvedActiveTab === tab.id
                ? "text-black font-medium tracking-normal bg-white/70"
                : "hover:text-neutral-800 text-neutral-500"
            )}
            style={{ WebkitTapHighlightColor: "transparent" }}
          >
            {resolvedActiveTab === tab.id && (
              <motion.span
                layoutId="bubble"
                className="absolute left-0 top-0 z-10 bg-black rounded-full w-1 h-full"
                transition={{ type: "spring", bounce: 0.19, duration: 0.4 }}
              />
            )}
            <motion.div
              initial={{ scale: 0 }}
              animate={{
                scale: 1,
                transition: {
                  type: "spring",
                  bounce: 0.2,
                  damping: 7,
                  duration: 0.4,
                  delay: index * 0.1,
                },
              }}
              variants={{
                default: { scale: 1 },
                ...(!(resolvedActiveTab === tab.id) && { hovered: { scale: 1.1 } }),
                ...(!(resolvedActiveTab === tab.id) && {
                  tapped: {
                    scale: 0.8,
                    transition: {
                      type: "spring",
                      bounce: 0.2,
                      damping: 7,
                      duration: 0.4,
                    },
                  },
                }),
              }}
              className="relative"
              transition={{ type: "spring" }}
            >
              {false && tab.id !== "food" && (
                <NewBadge className="absolute -top-2 -right-8 z-50" />
              )}

              <div className="relative size-7 flex items-center justify-center rounded-full bg-white/60">
                {tab.icon ? (
                  <span className="text-sm leading-none" aria-hidden="true">
                    {tab.icon}
                  </span>
                ) : (
                  <>
                    <video
                      key={`initial-${tab.id}`}
                      ref={(el) => {
                        if (el) videoRefs.current[String(tab.id)] = el;
                      }}
                      muted
                      playsInline
                      autoPlay
                      className={cn(
                        "absolute",
                        tabClicked ? "opacity-0" : "opacity-100"
                      )}
                    >
                      <source src={tab.initial_render_url} type="video/webm" />
                    </video>
                    <video
                      key={`clicked-${tab.id}`}
                      ref={(el) => {
                        if (el) videoRefs.current[String(tab.id)] = el;
                      }}
                      muted
                      playsInline
                      autoPlay
                      className={cn(
                        "absolute",
                        tabClicked ? "opacity-100" : "opacity-0"
                      )}
                    >
                      <source src={tab.video_url} type="video/webm" />
                    </video>
                  </>
                )}
              </div>
            </motion.div>
            <span className="text-[11px] leading-tight whitespace-normal text-left break-words">
              {tab.label}
            </span>
          </motion.button>
        ))}
      </div>
    </div>
  );
}

export { Component };
