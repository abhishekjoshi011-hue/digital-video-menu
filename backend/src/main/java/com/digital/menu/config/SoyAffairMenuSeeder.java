package com.digital.menu.config;

import com.digital.menu.model.Dish;
import com.digital.menu.repository.DishRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SoyAffairMenuSeeder implements CommandLineRunner {
    private final DishRepository dishRepository;

    @Value("${app.seed.soy-affair.enabled:true}")
    private boolean enabled;

    @Value("${app.seed.soy-affair.tenant-id:soy affair}")
    private String tenantId;

    public SoyAffairMenuSeeder(DishRepository dishRepository) {
        this.dishRepository = dishRepository;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }

        removeLegacyTestDishes(tenantId);
        List<Dish> dishes = buildMenu(tenantId);
        for (Dish incoming : dishes) {
            Dish existing = dishRepository
                .findByTenantIdAndName(tenantId, incoming.getName())
                .orElseGet(Dish::new);

            existing.setTenantId(tenantId);
            existing.setName(incoming.getName());
            existing.setDescription(incoming.getDescription());
            existing.setCategory(incoming.getCategory());
            existing.setType(incoming.getType());
            existing.setPrice(incoming.getPrice());

            // DB is source of truth for media URLs.
            // Only seed these fields when DB does not already have a value.
            if (isBlank(existing.getImageUrl())) {
                existing.setImageUrl(isBlank(incoming.getImageUrl()) ? "" : incoming.getImageUrl().trim());
            }
            if (isBlank(existing.getVideoUrl())) {
                existing.setVideoUrl(isBlank(incoming.getVideoUrl()) ? "" : incoming.getVideoUrl().trim());
            }

            dishRepository.save(existing);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void removeLegacyTestDishes(String tenant) {
        dishRepository.deleteByTenantIdAndNameIn(
            tenant,
            List.of(
                "Spaghetti Carbonara",
                "Speggeti Carbonara",
                "spaghetti carbonara",
                "speggeti carbonara",
                "Truffle Burrata",
                "truffle burrata"
            )
        );
    }

    private List<Dish> buildMenu(String tenant) {
        List<Dish> menu = new ArrayList<>();

        add(menu, tenant, "Set Menu", "veg", "Lunch Affair Set Menu (Veg)", "Veg lunch set menu.", 630);
        add(menu, tenant, "Set Menu", "non-veg", "Lunch Affair Set Menu (Non Veg)", "Non-veg lunch set menu.", 690);
        add(menu, tenant, "Add Ons", "veg", "Starter Add On - Cottage Cheese", "Set menu starter add-on.", 300);
        add(menu, tenant, "Add Ons", "non-veg", "Starter Add On - Fish (10 Pc)", "Set menu starter add-on.", 380);
        add(menu, tenant, "Add Ons", "non-veg", "Starter Add On - Prawn (6 Pc)", "Set menu starter add-on.", 420);
        add(menu, tenant, "Add Ons", "veg", "Mains Add On - Cottage Cheese", "Set menu mains add-on.", 350);
        add(menu, tenant, "Add Ons", "non-veg", "Mains Add On - Fish (10 Pc)", "Set menu mains add-on.", 400);
        add(menu, tenant, "Add Ons", "non-veg", "Mains Add On - Prawn (6 Pc)", "Set menu mains add-on.", 440);
        add(menu, tenant, "Add Ons", "non-veg", "Chicken Add On For Rice & Noodles", "Set menu rice/noodles add-on.", 100);

        add(menu, tenant, "Tea", "veg", "Jasmine Tea", "Jasmine Tea", 150);
        add(menu, tenant, "Tea", "veg", "Hibiscus Cinnamon Green Tea", "Hibiscus and cinnamon green tea.", 150);

        add(menu, tenant, "Beverages", "veg", "Bottled Water", "Packaged drinking water.", 30);
        add(menu, tenant, "Beverages", "veg", "Aerated Drinks (By Glass)", "Pepsi, 7up, Mirinda, M.Dew.", 70);
        add(menu, tenant, "Beverages", "veg", "Fresh Lime Soda", "Salty, sweet or mix.", 160);
        add(menu, tenant, "Beverages", "veg", "Masala Lemonade", "Refreshing masala lemonade.", 200);
        add(menu, tenant, "Beverages", "veg", "Lemon Ice Tea", "Lemon iced tea.", 200);
        add(menu, tenant, "Beverages", "veg", "Peach Ice Tea", "Peach iced tea.", 200);

        add(menu, tenant, "Mocktails", "veg", "Mint Mojito", "Mint mojito mocktail.", 220);
        add(menu, tenant, "Mocktails", "veg", "Chilli Guava Cooler", "Guava cooler with chilli.", 220);
        add(menu, tenant, "Mocktails", "veg", "Kiwi Jasmine Mojito", "Kiwi and jasmine mojito.", 230);
        add(menu, tenant, "Mocktails", "veg", "Garden Punch", "Signature fruit punch.", 230);
        add(menu, tenant, "Mocktails", "veg", "Lemongrass Lust", "Lemongrass mocktail.", 230);
        add(menu, tenant, "Mocktails", "veg", "Dew Of Sea", "Refreshing sea-inspired cooler.", 230);
        add(menu, tenant, "Mocktails", "veg", "Wasabi Martini", "Non-alcoholic wasabi martini.", 230);

        addSoupTriple(menu, tenant, "Aromatic Peppery Lemon Soup", "Clear soup infused with aromatic pepper and lemon.", 200, 210, 240);
        addSoupTriple(menu, tenant, "Burnt Garlic Clear Soup", "Clear soup elevated with burnt garlic aroma.", 200, 210, 240);
        addSoupTriple(menu, tenant, "Tom Yum Soup", "Thai-inspired sour and spicy clear soup.", 210, 230, 260);
        addSoupTriple(menu, tenant, "Wonton Clear Soup", "Delicate broth with stuffed wontons.", 210, 230, 260);
        addSoupTriple(menu, tenant, "Thukpa", "Tibetan style noodle soup.", 280, 340, 380);
        addSoupTriple(menu, tenant, "Hakka Soup", "Rich mix of vegetables and spices.", 200, 220, 250);
        addSoupTriple(menu, tenant, "Sweet Corn Soup", "Sweet Corn Soup", 200, 220, 250);
        addSoupTriple(menu, tenant, "Four Island Soup", "Fusion soup with layered flavors.", 220, 240, 270);
        addSoupTriple(menu, tenant, "Hot & Sour Soup", "Spicy and tangy soup.", 220, 240, 270);
        add(menu, tenant, "Soups", "non-veg", "Crab Meat Soup", "Crab meat soup.", 270);

        add(menu, tenant, "Dumplings", "veg", "Corn & Spinach Dumpling (6 Pc)", "Corn and spinach dumplings.", 370);
        add(menu, tenant, "Dumplings", "veg", "Vegetable Basil Dumpling (6 Pc)", "Vegetable dumpling with basil.", 370);
        add(menu, tenant, "Dumplings", "veg", "Vegetable Crystal Dumpling (6 Pc)", "Crystal dumpling with mixed vegetables.", 380);
        add(menu, tenant, "Dumplings", "veg", "Vegetable Oriental Dumpling (6 Pc)", "Oriental style vegetable dumpling.", 380);
        add(menu, tenant, "Dumplings", "veg", "Cheesy Zucchini Dumpling (6 Pc)", "Zucchini and cheese dumpling.", 380);
        add(menu, tenant, "Dumplings", "veg", "Veg Dimsum Platter (8 Pc)", "Assorted veg dimsum platter.", 490);
        add(menu, tenant, "Dumplings", "non-veg", "Chicken Siu-Mai (6 Pc)", "Chicken siu-mai dumplings.", 390);
        add(menu, tenant, "Dumplings", "non-veg", "Chicken Basil Dumpling (6 Pc)", "Chicken basil dumplings.", 390);
        add(menu, tenant, "Dumplings", "non-veg", "Chicken & Burnt Garlic Dumpling (6 Pc)", "Chicken and burnt garlic dumplings.", 410);
        add(menu, tenant, "Dumplings", "non-veg", "Chicken Hong Kong Dumpling (6 Pc)", "Hong Kong style chicken dumplings.", 410);
        add(menu, tenant, "Dumplings", "non-veg", "Chicken & Shiitake Mushroom Dumpling (6 Pc)", "Chicken and shiitake dumplings.", 410);
        add(menu, tenant, "Dumplings", "non-veg", "Thai Style Chicken Dumplings (6 Pc)", "Thai style chicken dumplings.", 410);
        add(menu, tenant, "Dumplings", "non-veg", "Chicken Dimsum Platter (8 Pc)", "Assorted chicken dimsum platter.", 520);

        addSushiPair(menu, tenant, "veg", "Creamy Avocado Battleship", "Avocado, cucumber and cream cheese.", 470, 710);
        addSushiPair(menu, tenant, "veg", "Panko Crusted Roll", "Tempura fried broccoli and mushroom roll.", 470, 710);
        addSushiPair(menu, tenant, "veg", "Cream Cheese Avocado Roll", "Cream cheese and avocado roll.", 470, 710);
        addSushiPair(menu, tenant, "veg", "Veg California Roll", "Asparagus, cream cheese and avocado.", 470, 710);
        addSushiPair(menu, tenant, "veg", "Asparagus Tempura Roll", "Tempura asparagus and avocado roll.", 470, 710);
        addSushiPair(menu, tenant, "non-veg", "California Roll", "Crabstick and avocado roll with caviar.", 510, 760);
        addSushiPair(menu, tenant, "non-veg", "Prawn Tempura Roll", "Tempura prawn and crabstick roll.", 510, 760);
        addSushiPair(menu, tenant, "non-veg", "Philadelphia Roll", "Cream cheese, avocado and salmon roll.", 510, 760);
        addSushiPair(menu, tenant, "non-veg", "Tuna Roll", "Tuna with sriracha wrapped in sushi rice.", 510, 760);
        addSushiPair(menu, tenant, "non-veg", "Caterpillar Roll", "Avocado, cream cheese and crabstick roll.", 510, 760);
        addSushiPair(menu, tenant, "non-veg", "Creamy Prawn Battleship Roll", "Chopped shrimps with cream cheese.", 510, 760);

        add(menu, tenant, "Bao", "veg", "Spicy Cottage Cheese Bao (2 Pc)", "Fried cottage cheese bao.", 380);
        add(menu, tenant, "Bao", "veg", "Vietnamese Bao (2 Pc)", "Vegetable bao with in-house mayo.", 380);
        add(menu, tenant, "Bao", "veg", "Cream Cheese Avocado Bao (2 Pc)", "Avocado and cream cheese bao.", 380);
        add(menu, tenant, "Bao", "non-veg", "Katsu Chicken Bao (2 Pc)", "Bread crumb fried chicken bao.", 390);
        add(menu, tenant, "Bao", "non-veg", "Soy Chilli Chicken Bao (2 Pc)", "Shredded chicken bao with soy chilli.", 390);
        add(menu, tenant, "Bao", "non-veg", "Chicken Lemongrass Bao (2 Pc)", "Lemongrass chicken bao.", 390);
        add(menu, tenant, "Bao", "non-veg", "Thai Style Fish Bao (2 Pc)", "Thai spiced fish bao.", 430);
        add(menu, tenant, "Bao", "non-veg", "Prawn Dynamite Bao (2 Pc)", "Tempura prawn bao.", 430);

        add(menu, tenant, "Starters", "veg", "Veg Shanghai Spring Roll", "Crispy Shanghai spring rolls.", 350);
        add(menu, tenant, "Starters", "veg", "Crispy Potato", "Hot & spicy or honey chilli potato.", 350);
        add(menu, tenant, "Starters", "veg", "Zucchini Herbal Salt", "Fresh zucchini with herbal salt.", 370);
        add(menu, tenant, "Starters", "veg", "Minced Vegetable Pattie In Chilli Soy", "Spicy soy vegetable pattie.", 370);
        add(menu, tenant, "Starters", "veg", "Crispy Assorted Vegetables", "Thai style, plum or hot & spicy.", 370);
        add(menu, tenant, "Starters", "veg", "Crispy Corn Spicy Pepper", "Crispy corn with pepper kick.", 380);
        add(menu, tenant, "Starters", "veg", "Wok Tossed Mushroom", "Teriyaki, hunan or roasted garlic.", 380);
        add(menu, tenant, "Starters", "veg", "Silken Tofu", "Sichuan chilli, hunan or spicy bean.", 390);
        add(menu, tenant, "Starters", "veg", "Crispy Lotus Stem", "Honey chilli or black pepper curry leaves.", 390);
        add(menu, tenant, "Starters", "veg", "Water Chestnut", "Honey chilli or black pepper curry leaves.", 390);
        add(menu, tenant, "Starters", "veg", "Cottage Cheese", "Jiang chilli, butter garlic or black pepper curry leaves.", 390);
        add(menu, tenant, "Starters", "veg", "Stir Fry Oriental Greens", "Stir fried oriental greens.", 390);

        add(menu, tenant, "Starters", "non-veg", "Chicken Shanghai Spring Roll", "Crispy chicken spring roll.", 370);
        add(menu, tenant, "Starters", "non-veg", "Drums Of Heaven", "Shandong sweet-spicy or Hongkong style.", 380);
        add(menu, tenant, "Starters", "non-veg", "Wok Tossed Chicken Wings", "Hot & spicy, BBQ or teriyaki.", 380);
        add(menu, tenant, "Starters", "non-veg", "Soy Chilli Chicken", "Soy chilli tossed chicken.", 420);
        add(menu, tenant, "Starters", "non-veg", "Chicken Teriyaki", "Chicken in teriyaki glaze.", 420);
        add(menu, tenant, "Starters", "non-veg", "Plum Chilli Chicken", "Sweet and spicy plum chilli chicken.", 420);
        add(menu, tenant, "Starters", "non-veg", "Sliced Chicken", "Thai style or spicy bean sauce.", 420);
        add(menu, tenant, "Starters", "non-veg", "Wok Tossed Diced Chicken", "Hunan, kung pao, roasted garlic, sriracha, sambal, pepper garlic.", 420);
        add(menu, tenant, "Starters", "non-veg", "BBQ Grilled Pepper Chicken", "Smoky pepper grilled chicken.", 420);
        add(menu, tenant, "Starters", "non-veg", "Fish In Choice Of Your Sauce (Starter)", "Pepper garlic, hunan, hot & spicy, spicy bean, roasted garlic.", 450);
        add(menu, tenant, "Starters", "non-veg", "Prawn In Choice Of Your Sauce (Starter)", "Butter garlic, pepper garlic, hunan, sambal, spicy bean, roasted garlic.", 480);

        addFourTier(menu, tenant, "Noodles", "Hakka Noodles", "Hakka Noodles", 340, 350, 380, 400);
        addFourTier(menu, tenant, "Noodles", "Sichuan Hakka Noodles", "Fiery sichuan noodles.", 340, 350, 380, 400);
        addFourTier(menu, tenant, "Noodles", "Butter Garlic Noodles", "Buttery garlic noodles.", 340, 350, 380, 400);
        addFourTier(menu, tenant, "Noodles", "Chilli Garlic Noodles", "Spicy chilli garlic noodles.", 340, 350, 380, 400);
        addFourTier(menu, tenant, "Noodles", "Chilli Basil Noodles", "Aromatic chilli basil noodles.", 340, 350, 380, 400);
        addFourTier(menu, tenant, "Noodles", "Pad Thai Noodles", "Flat noodles in sweet-savoury-sour sauce.", 370, null, 400, 420);
        addFourTier(menu, tenant, "Noodles", "BBQ Noodles", "Smoky barbecue noodles.", 370, 380, 400, 420);
        addFourTier(menu, tenant, "Noodles", "Hongkong Noodles", "Hongkong style noodles.", 370, 380, 400, 420);
        addFourTier(menu, tenant, "Noodles", "Black Pepper Noodles", "Peppery noodle preparation.", 370, 380, 400, 420);
        addFourTier(menu, tenant, "Noodles", "Mi Goreng", "Indonesian style spicy noodles.", null, null, 400, 440);
        addFourTier(menu, tenant, "Noodles", "Panfried Noodles", "Crispy panfried noodles.", 470, null, 500, 540);

        add(menu, tenant, "Rice", "veg", "Steamed Rice", "Steamed rice side.", 240);
        addFourTier(menu, tenant, "Rice", "Fried Rice", "Fried Rice", 340, 350, 380, 400);
        addFourTier(menu, tenant, "Rice", "Burnt Garlic Fried Rice", "Fried rice with burnt garlic.", 340, 350, 380, 400);
        addFourTier(menu, tenant, "Rice", "Sichuan Fried Rice", "Sichuan style fried rice.", 340, 350, 380, 400);
        addFourTier(menu, tenant, "Rice", "Hunan Fried Rice", "Hunan style fried rice.", 340, 350, 380, 400);
        addFourTier(menu, tenant, "Rice", "Chilli Basil Fried Rice", "Fried rice with chilli basil.", 360, 370, 400, 430);
        addFourTier(menu, tenant, "Rice", "Fragrant Butter Rice", "Long grain rice with butter.", 360, 370, 400, 430);
        addFourTier(menu, tenant, "Rice", "Shiitake Mushroom Fried Rice", "Fried rice with shiitake mushroom.", 360, 370, 400, 430);
        addFourTier(menu, tenant, "Rice", "Kimchi Fried Rice", "Spicy tangy kimchi fried rice.", 360, 370, 400, 430);
        addFourTier(menu, tenant, "Rice", "Black Pepper Fried Rice", "Bold pepper fried rice.", 360, 370, 400, 430);
        addFourTier(menu, tenant, "Rice", "Nasi Goreng Fried Rice", "Indonesian style fried rice.", null, null, 400, 440);

        add(menu, tenant, "Main Course", "veg", "Mix Vegetables In Hot Garlic Sauce", "Spicy mixed vegetables in hot garlic sauce.", 380);
        add(menu, tenant, "Main Course", "veg", "Minced Vegetable Pattie In Chilli Soy Sauce", "Vegetable pattie in chilli soy sauce.", 380);
        add(menu, tenant, "Main Course", "veg", "Exotic Vegetables In Choice Of Sauce", "Spicy basil, smoked chilli, black bean, fragrant chilli, roasted garlic.", 410);
        add(menu, tenant, "Main Course", "veg", "Cottage Cheese In Ginger Coriander Sauce", "Cottage cheese in ginger coriander sauce.", 410);
        add(menu, tenant, "Main Course", "veg", "Mushroom In Choice Of Sauce", "Chilli basil or roasted garlic.", 410);
        add(menu, tenant, "Main Course", "veg", "Broccoli Corn Chestnut In Black Bean Sauce", "Broccoli, corn and chestnut in black bean sauce.", 410);
        add(menu, tenant, "Main Course", "veg", "Chinese Green & Tofu In Hunan", "Chinese greens and tofu in hunan sauce.", 410);

        addThreeTier(menu, tenant, "Main Course", "Asian Curry", "Thai style asian curry without rice.", 440, 480, 520);
        addThreeTier(menu, tenant, "Main Course", "Red Thai Curry", "Red thai curry without rice.", 440, 480, 520);
        addThreeTier(menu, tenant, "Main Course", "Green Thai Curry", "Green thai curry without rice.", 440, 480, 520);

        add(menu, tenant, "Main Course", "non-veg", "Sliced Chicken In Choice Of Sauce", "Chilli basil, chilli oyster, hot garlic, black bean, fragrant chilli.", 440);
        add(menu, tenant, "Main Course", "non-veg", "Tsing-Hoi Chicken", "Tender chicken with cashew nuts.", 440);
        add(menu, tenant, "Main Course", "non-veg", "Shredded Chicken In Black Pepper Sauce", "Shredded chicken in black pepper sauce.", 440);
        add(menu, tenant, "Main Course", "non-veg", "Chicken Kaprao", "Spicy chicken with holy basil.", 440);
        add(menu, tenant, "Main Course", "non-veg", "General Tso's Chicken", "Diced chicken in tso's sauce.", 460);
        add(menu, tenant, "Main Course", "non-veg", "Braised Chicken In Smoked Chilli Sauce", "Braised chicken in smoked chilli sauce.", 460);
        add(menu, tenant, "Main Course", "non-veg", "Chicken In Roasted Garlic Sauce", "Chicken in roasted garlic sauce.", 460);
        add(menu, tenant, "Main Course", "non-veg", "Fish In Choice Of Sauce (Main Course)", "Spicy basil, sichuan, smoked chilli, black bean, roasted garlic.", 470);
        add(menu, tenant, "Main Course", "non-veg", "Prawn In Choice Of Sauce (Main Course)", "Chilli oyster, sichuan, smoked chilli, black bean, roasted garlic.", 500);

        add(menu, tenant, "Combo Meals", "veg", "Combo Meal Veg", "Vegetable fried rice or vegetable burnt garlic noodles with veg mains choice.", 440);
        add(menu, tenant, "Combo Meals", "non-veg", "Combo Meal Chicken", "Egg fried rice or egg burnt garlic noodles with chicken mains choice.", 480);

        add(menu, tenant, "Desserts", "veg", "Darsan", "Honey noodles dessert.", 270);
        add(menu, tenant, "Desserts", "veg", "Chocolate Roll With Vanilla Ice Cream", "Chocolate roll with vanilla ice cream.", 300);
        add(menu, tenant, "Desserts", "veg", "Sizzling Hot Brownie With Vanilla Ice Cream", "Sizzling brownie with vanilla ice cream.", 300);

        return menu;
    }

    private void addSoupTriple(List<Dish> list, String tenant, String baseName, String description, double veg, double chicken, double seafood) {
        add(list, tenant, "Soups", "veg", baseName + " (Veg)", description, veg);
        add(list, tenant, "Soups", "non-veg", baseName + " (Chicken)", description, chicken);
        add(list, tenant, "Soups", "non-veg", baseName + " (Seafood)", description, seafood);
    }

    private void addSushiPair(List<Dish> list, String tenant, String type, String baseName, String description, double fourPcPrice, double eightPcPrice) {
        add(list, tenant, "Sushi", type, baseName + " (4 Pc)", description, fourPcPrice);
        add(list, tenant, "Sushi", type, baseName + " (8 Pc)", description, eightPcPrice);
    }

    private void addThreeTier(List<Dish> list, String tenant, String category, String baseName, String description, Integer vegPrice, Integer chickenPrice, Integer prawnPrice) {
        if (vegPrice != null) {
            add(list, tenant, category, "veg", baseName + " (Veg)", description, vegPrice);
        }
        if (chickenPrice != null) {
            add(list, tenant, category, "non-veg", baseName + " (Chicken)", description, chickenPrice);
        }
        if (prawnPrice != null) {
            add(list, tenant, category, "non-veg", baseName + " (Prawn)", description, prawnPrice);
        }
    }

    private void addFourTier(List<Dish> list, String tenant, String category, String baseName, String description, Integer vegPrice, Integer eggPrice, Integer chickenPrice, Integer prawnPrice) {
        if (vegPrice != null) {
            add(list, tenant, category, "veg", baseName + " (Veg)", description, vegPrice);
        }
        if (eggPrice != null) {
            add(list, tenant, category, "non-veg", baseName + " (Egg)", description, eggPrice);
        }
        if (chickenPrice != null) {
            add(list, tenant, category, "non-veg", baseName + " (Chicken)", description, chickenPrice);
        }
        if (prawnPrice != null) {
            add(list, tenant, category, "non-veg", baseName + " (Prawn)", description, prawnPrice);
        }
    }

    private void add(List<Dish> list, String tenant, String category, String type, String name, String description, double price) {
        add(list, tenant, category, type, name, description, price, "");
    }

    private void add(
        List<Dish> list,
        String tenant,
        String category,
        String type,
        String name,
        String description,
        double price,
        String videoUrl
    ) {
        Dish d = new Dish();
        String rawDescription = description == null ? "" : description.trim();
        boolean menuStyleDescription =
            rawDescription.contains("/") ||
            rawDescription.contains("(") ||
            rawDescription.toLowerCase().contains("choice of");
        String customerDescription = menuStyleDescription ? rawDescription : name;

        d.setTenantId(tenant);
        d.setCategory(category);
        d.setType(type);
        d.setName(name);
        d.setDescription(customerDescription);
        d.setPrice(price);
        d.setImageUrl("");
        d.setVideoUrl(videoUrl == null ? "" : videoUrl.trim());
        list.add(d);
    }
}
