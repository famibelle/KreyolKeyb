//
//  UserDefaultsManager.swift
//  Klavyé Kréyòl Karukera
//
//  Created by Potomitan™ on 01/11/2025.
//

import Foundation

class UserDefaultsManager {
    
    static let shared = UserDefaultsManager()
    
    private let userDefaults: UserDefaults?
    private let appGroupID = "group.com.potomitan.kreyolkeyboard"
    
    // Keys
    private let kHasSeenOnboardingKey = "hasSeenOnboarding"
    private let kIsKeyboardEnabledKey = "isKeyboardEnabled"
    private let kSuggestionsEnabledKey = "suggestionsEnabled"
    private let kAutoCapitalizationKey = "autoCapitalization"
    private let kHapticFeedbackKey = "hapticFeedback"
    
    private init() {
        self.userDefaults = UserDefaults(suiteName: appGroupID)
    }
    
    // MARK: - Onboarding
    var hasSeenOnboarding: Bool {
        get { userDefaults?.bool(forKey: kHasSeenOnboardingKey) ?? false }
        set { userDefaults?.set(newValue, forKey: kHasSeenOnboardingKey) }
    }
    
    // MARK: - Keyboard Status
    var isKeyboardEnabled: Bool {
        get { userDefaults?.bool(forKey: kIsKeyboardEnabledKey) ?? false }
        set { userDefaults?.set(newValue, forKey: kIsKeyboardEnabledKey) }
    }
    
    // MARK: - Settings
    var suggestionsEnabled: Bool {
        get { userDefaults?.bool(forKey: kSuggestionsEnabledKey) ?? true }
        set { userDefaults?.set(newValue, forKey: kSuggestionsEnabledKey) }
    }
    
    var autoCapitalizationEnabled: Bool {
        get { userDefaults?.bool(forKey: kAutoCapitalizationKey) ?? true }
        set { userDefaults?.set(newValue, forKey: kAutoCapitalizationKey) }
    }
    
    var hapticFeedbackEnabled: Bool {
        get { userDefaults?.bool(forKey: kHapticFeedbackKey) ?? true }
        set { userDefaults?.set(newValue, forKey: kHapticFeedbackKey) }
    }
    
    // MARK: - Reset
    func resetAll() {
        guard let domain = Bundle.main.bundleIdentifier else { return }
        userDefaults?.removePersistentDomain(forName: domain)
        userDefaults?.synchronize()
    }
}
