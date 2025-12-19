package com.flowfoundation.wallet.utils

import java.security.SecureRandom
import kotlin.math.abs

object RandomUsernameGenerator {

    private val fruits = listOf(
        "Apple", "Apricot", "Avocado", "Banana", "Cherry", "Coconut", "Date", "Fig", "Grape",
        "Guava", "Kiwi", "Lemon", "Lime", "Mango", "Melon", "Orange", "Papaya", "Peach", "Pear",
        "Plum", "Pomelo", "Quince", "Soursop", "Currant", "Sapote", "Longan", "Durian", "Kumquat",
        "Lychee", "Acai", "Berry", "Citrus", "Olive", "Raisin", "Prune", "Cassia", "Yuzu"
    )

    private val animals = listOf(
        "Ant", "Bear", "Beaver", "Bee", "Bird", "Bobcat", "Buffalo", "Camel", "Cat", "Cheetah",
        "Chicken", "Cobra", "Cow", "Crab", "Deer", "Dog", "Dolphin", "Duck", "Eagle", "Falcon",
        "Ferret", "Fish", "Fox", "Frog", "Giraffe", "Goat", "Goose", "Hamster", "Hawk", "Hippo",
        "Horse", "Jaguar", "Koala", "Lemur", "Leopard", "Lion", "Lizard", "Llama", "Lobster",
        "Monkey", "Moose", "Mouse", "Octopus", "Otter", "Owl", "Panda", "Panther", "Parrot",
        "Penguin", "Pig", "Rabbit", "Raccoon", "Ram", "Rat", "Raven", "Seal", "Shark", "Sheep",
        "Skunk", "Sloth", "Snail", "Snake", "Spider", "Tiger", "Toad", "Turtle", "Whale", "Wolf",
        "Wombat", "Zebra", "Badger", "Bat", "Beetle", "Bison", "Boar", "Caribou", "Clam", "Coyote",
        "Crow", "Donkey", "Eel", "Emu", "Firefly", "Gazelle", "Gecko", "Gopher", "Grouse", "Heron",
        "Hyena", "Iguana", "Jackal", "Jay", "Koi", "Lark", "Lynx", "Magpie", "Mallard", "Mantis",
        "Mink", "Mole", "Moth", "Narwhal", "Newt", "Ocelot", "Orca", "Pelican", "Pigeon", "Quail",
        "Robin", "Rooster", "Salmon", "Sardine", "Sealion", "Shrimp", "Swan", "Termite", "Toucan",
        "Trout", "Vulture", "Walrus", "Weasel", "Yak", "Zebu", "Alpaca", "Condor", "Finch",
        "Hornet", "Meerkat", "Osprey", "Possum", "Puffin", "Shrew", "Viper", "Wasp", "Wren",
        "Corgi", "Husky", "Puma", "Ibis", "Crane", "Stork", "Hound", "Dingo", "Betta", "Cicada",
        "Marlin", "Cougar"
    )

    private val nature = listOf(
        "River", "Ocean", "Sky", "Cloud", "Rain", "Sun", "Moon", "Star", "Comet", "Breeze",
        "Storm", "Forest", "Tree", "Leaf", "Rock", "Stone", "Pebble", "Hill", "Valley", "Canyon",
        "Desert", "Sand", "Wave", "Tide", "Lake", "Pond", "Stream", "Coral", "Reef", "Glacier",
        "Aurora", "Shadow", "Light", "Flame", "Fire", "Smoke", "Mist", "Dawn", "Dusk", "Sunset",
        "Sunrise", "Field", "Garden", "Flower", "Petal", "Vine", "Root", "Branch", "Seed",
        "Berry", "Moss", "Fern", "Willow", "Oak", "Pine", "Maple", "Cedar", "Meadow", "Prairie",
        "Savanna", "Jungle", "Island", "Lagoon", "Bay", "Shore", "Cave", "Cliff", "Crystal",
        "Gem", "Amber", "Pearl", "Quartz", "Emerald", "Topaz", "Volcano", "Geyser", "Thunder",
        "Horizon", "Echo", "Boulder", "Dune", "Frost", "Galaxy", "Orbit", "Planet", "Meteor",
        "Cosmos", "Fjord", "Grove", "Glade", "Bluff", "Delta", "Estuary", "Peak", "Ridge",
        "Ravine", "Butte", "Mesa", "Cove", "Inlet", "Grotto", "Cavern", "Knoll", "Dale", "Glen",
        "Heath", "Moor", "Fen", "Bog", "Marsh", "Swamp", "Basin", "Oasis", "Tundra", "Steppe",
        "Taiga", "Vale", "Blaze", "Ember", "Spark", "Ash", "Cinder", "Flare", "Glow", "Beam",
        "Ray", "Haze", "Fog", "Dew", "Rime", "Snow", "Hail", "Sleet", "Gust", "Gale", "Zephyr",
        "Squall", "Tempest", "Cyclone", "Nebula", "Quasar", "Pulsar", "Nova", "Void", "Abyss",
        "Zenith", "Nadir", "Eclipse"
    )

    private val adjectives = listOf(
        "Brave", "Calm", "Clever", "Cool", "Cozy", "Curious", "Daring", "Eager", "Fancy", "Gentle",
        "Glowing", "Golden", "Happy", "Humble", "Jolly", "Kind", "Lively", "Lucky", "Mellow",
        "Mighty", "Noble", "Playful", "Polite", "Proud", "Quick", "Quiet", "Radiant", "Shiny",
        "Silly", "Smart", "Smiling", "Snug", "Sparkly", "Swift", "Tiny", "Vibrant", "Warm", "Witty",
        "Zany", "Bright", "Bold", "Dynamic", "Epic", "Fierce", "Gleeful", "Joyful", "Lovely",
        "Merry", "Neat", "Peppy", "Plucky", "Posh", "Relaxed", "Serene", "Smooth", "Spunky",
        "Sunny", "Sweet", "Thrifty", "Tricky", "Upbeat", "Valiant", "Zealous", "Alert", "Astute",
        "Chill", "Crisp", "Dapper", "Keen", "Lush", "Perky", "Poised", "Prime", "Pure", "Rare",
        "Robust", "Sage", "Sharp", "Sleek", "Snappy", "Solid", "Spry", "Stable", "Steady",
        "Stealth", "Stern", "Stout", "Suave", "Super", "Tender", "Tidy", "True", "Trusty",
        "Ultra", "Unique", "Upright", "Urbane", "Vivid", "Wary", "Wild", "Wise", "Agile",
        "Blessed", "Caring", "Cosmic", "Crafty", "Divine", "Elated", "Fluent", "Fresh", "Gifted",
        "Grand", "Heroic", "Honest", "Ideal"
    )

    private val allWords = fruits + animals + nature + adjectives
    private val random = SecureRandom()

    private fun pick(list: List<String>): String {
        return list[abs(random.nextInt()) % list.size]
    }

    fun generateRandomUsername(): String {
        // Filter to only short words (max 7 chars) to ensure 3 words fit in 20 chars
        val shortWords = allWords.filter { it.length <= 7 }
        
        var username = ""
        var attempts = 0
        val maxAttempts = 100

        while (attempts < maxAttempts) {
            val word1 = pick(shortWords)
            var word2 = pick(shortWords)
            while (word2 == word1) {
                word2 = pick(shortWords)
            }
            
            var word3 = pick(shortWords)
            while (word3 == word1 || word3 == word2) {
                word3 = pick(shortWords)
            }

            val words = listOf(word1, word2, word3)
            val adjectiveIndex = words.indexOfFirst { adjectives.contains(it) }

            if (adjectiveIndex != -1) {
                // Put adjective first
                val adjective = words[adjectiveIndex]
                val otherWords = words.filterIndexed { index, _ -> index != adjectiveIndex }
                username = "$adjective${otherWords[0]}${otherWords[1]}"
            } else {
                username = "$word1$word2$word3"
            }

            if (username.length in 3..20) {
                return username
            }

            attempts++
        }

        // Fallback
        val fallback = shortWords.take(3).joinToString("")
        return fallback.take(20)
    }
}
