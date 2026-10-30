-- Controller Menus: controller support on the 3.3.5a login screens.
-- The app's controller profile (shared with ConsolePortLK) sends the pad as F-keys; the stock screens already understand
-- Enter, Escape and the arrows, so pad keys are translated and passed to Blizzard's own handlers.

local PAD = {
    F1 = "ENTER",   -- A
    F2 = "ESCAPE",  -- B
    F3 = "PAD_X",
    F4 = "PAD_Y",
    F5 = "UP",
    F6 = "DOWN",
    F7 = "LEFT",
    F8 = "RIGHT",
    F9 = "PAD_L1",
    F10 = "PAD_R1",
}

-- Ignores a repeat of the same key within 150 ms.
local lastKey, lastTime = nil, 0
local function translate(key)
    local mapped = PAD[key] or key
    local now = GetTime()
    if mapped == lastKey and now - lastTime < 0.15 then
        return nil
    end
    lastKey, lastTime = mapped, now
    -- Some stock handlers read the global arg1 instead of their key argument.
    arg1 = mapped
    return mapped
end

local function wrap(name, withSelf)
    local original = _G[name]
    if not original then return end
    if withSelf then
        _G[name] = function(self, key)
            local mapped = translate(key)
            if mapped then original(self, mapped) end
        end
    else
        _G[name] = function(key)
            local mapped = translate(key)
            if mapped then original(mapped) end
        end
    end
    return original
end

local function hint(parent, text)
    if not parent then return end
    local line = parent:CreateFontString(nil, "OVERLAY", "GlueFontNormalSmall")
    line:SetPoint("TOP", parent, "TOP", 0, -12)
    line:SetText(text)
    return line
end

-- Servers and Log in with A. AndroWhereServers.lua carries the servers saved in the app; with Fill in password,
-- AndroWhereLoginData.lua (written for one launch only) carries their passwords. A filled password box is left
-- unfocused, so A reaches the login frame and logs in. L1 and R1 switch servers.
local servers = AndroWhereServers
local serverIndex = AndroWhereServerIndex or 1
local passwords = AndroWhereLoginPasswords or {}
local autoLogin = AndroWhereLoginAuto
AndroWhereServers, AndroWhereServerIndex, AndroWhereLoginPasswords, AndroWhereLoginAuto = nil, nil, nil, nil
local password = passwords[serverIndex]

local function fillPassword()
    if password and AccountLoginPasswordEdit:GetText() == "" then
        AccountLoginPasswordEdit:SetText(password)
    end
end

local loginHint = hint(AccountLoginUI or AccountLogin, "")
local serverLine = hint(AccountLoginUI or AccountLogin, "")
if serverLine then serverLine:SetPoint("TOP", AccountLoginUI or AccountLogin, "TOP", 0, -30) end

local function updateLoginHint()
    if loginHint then
        if not password then
            loginHint:SetText("B or a tap outside the boxes  Stop typing       Hold Select  Keyboard       B  Quit")
        elseif AccountLoginAccountEdit:GetText() ~= "" then
            loginHint:SetText(autoLogin and "Logging in. A  Log in again       B  Quit" or "A  Log in       Hold Select  Keyboard       B  Quit")
        else
            loginHint:SetText("Hold Select for the keyboard, type your account name and press Enter. Tick Remember Account Name to log in with A next time.")
        end
    end
    if serverLine then
        if servers and #servers > 1 then
            serverLine:SetText("Server: " .. servers[serverIndex].name .. "       L1 R1  Switch server")
        else
            serverLine:SetText("")
        end
    end
end

-- Auto login presses Log in once per game start, half a second after the screen shows. After a failed
-- login or a disconnect it waits for A, so a wrong password isn't sent again and again.
local function tryAutoLogin()
    if not (password and autoLogin) then return end
    -- The screen's focus calls can come before the account name is filled in, so check when it's due.
    autoLogin = nil
    local waited = 0
    local timer = CreateFrame("Frame", nil, AccountLogin)
    timer:SetScript("OnUpdate", function(self, elapsed)
        waited = waited + (elapsed or arg1 or 0)
        if waited < 0.5 then return end
        self:SetScript("OnUpdate", nil)
        updateLoginHint()
        if AccountLogin:IsShown() and AccountLoginAccountEdit:GetText() ~= "" and TOSAccepted()
            and not GlueDialog:IsShown() and not TOSFrame:IsShown() then
            fillPassword()
            AccountLogin_Login()
        end
    end)
end

-- The login screen gives the account or password box the keyboard as it shows. With a server's account name
-- and password from the app, both are filled and neither box keeps the keyboard.
local focusPassword, focusAccountName = AccountLogin_FocusPassword, AccountLogin_FocusAccountName
local function prepareLogin(focus)
    local server = servers and servers[serverIndex]
    if server and server.account ~= "" then
        AccountLoginAccountEdit:SetText(server.account)
    end
    fillPassword()
    updateLoginHint()
    if password and AccountLoginAccountEdit:GetText() ~= "" then
        AccountLoginPasswordEdit:ClearFocus()
        AccountLoginAccountEdit:ClearFocus()
        tryAutoLogin()
    elseif AccountLoginAccountEdit:GetText() == "" then
        focusAccountName()
    else
        focus()
    end
end
AccountLogin_FocusPassword = function() prepareLogin(focusPassword) end
AccountLogin_FocusAccountName = function() prepareLogin(focusAccountName) end

local function switchServer(step)
    if not (servers and #servers > 1) then return end
    serverIndex = serverIndex + step
    if serverIndex < 1 then serverIndex = #servers elseif serverIndex > #servers then serverIndex = 1 end
    local server = servers[serverIndex]
    SetCVar("realmList", server.realmlist)
    password = passwords[serverIndex]
    if server.account == "" then AccountLoginAccountEdit:SetText(GetSavedAccountName()) end
    AccountLoginPasswordEdit:SetText("")
    prepareLogin(focusPassword)
end

local function leaveBoxes()
    AccountLoginAccountEdit:ClearFocus()
    AccountLoginPasswordEdit:ClearFocus()
end

-- A focused box takes the keyboard, so L1 and R1 are also caught there, and B leaves the box
-- (as Enter with no name does on character creation) so the pad works again.
for _, box in ipairs({ AccountLoginAccountEdit, AccountLoginPasswordEdit }) do
    local boxKey = box:GetScript("OnKeyDown")
    box:SetScript("OnKeyDown", function(self, key, ...)
        local pad = PAD[key or arg1]
        if pad == "PAD_L1" or pad == "PAD_R1" then
            local mapped = translate(key or arg1)
            if mapped then switchServer(mapped == "PAD_L1" and -1 or 1) end
        elseif pad == "ESCAPE" then
            if translate(key or arg1) then leaveBoxes() end
        elseif boxKey then
            boxKey(self, key, ...)
        end
    end)
end

-- Tapping or clicking anywhere on the screen outside a box or button leaves the boxes, as on character creation.
AccountLogin:EnableMouse(true)
local loginMouseDown = AccountLogin:GetScript("OnMouseDown")
AccountLogin:SetScript("OnMouseDown", function(...)
    leaveBoxes()
    if loginMouseDown then loginMouseDown(...) end
end)

-- Pad keys only reach this when no box is focused. B goes to the stock Escape, which quits the game
-- (or closes the help and survey panels first).
local accountLoginKey = AccountLogin_OnKeyDown
AccountLogin_OnKeyDown = function(key)
    local mapped = translate(key)
    if not mapped then return end
    if mapped == "PAD_L1" then
        switchServer(-1)
    elseif mapped == "PAD_R1" then
        switchServer(1)
    else
        if mapped == "ENTER" then fillPassword() end
        accountLoginKey(mapped)
    end
end
updateLoginHint()

-- Character select: the D-pad picks, A enters the world, B goes back, Y changes realm.
local characterSelectKey = CharacterSelect_OnKeyDown
CharacterSelect_OnKeyDown = function(self, key)
    local mapped = translate(key)
    if not mapped then return end
    if mapped == "PAD_Y" then
        CharacterSelect_ChangeRealm()
    elseif mapped == "PAD_X" then
        if CharSelectCreateCharacterButton:IsShown() then
            CharacterSelect_SelectCharacter(CharacterSelect.createIndex)
        end
    else
        characterSelectKey(self, mapped)
    end
end
hint(CharacterSelect, "D-pad  Choose       A  Enter world       X  New character       Y  Change realm       B  Back")

-- Character creation: up and down pick a row, left and right change it. The row is lit with the
-- stock button highlight. A on the name row opens the name box for typing; elsewhere it creates.
local createRow = 1
local function createRows()
    local rows = { "gender", "race", "class" }
    for i = 1, NUM_CHAR_CUSTOMIZATIONS do
        if _G["CharacterCustomizationButtonFrame" .. i]:IsShown() then
            table.insert(rows, i)
        end
    end
    table.insert(rows, "name")
    return rows
end

local function rowButtons(row)
    if row == "gender" then
        return { CharacterCreateGenderButtonMale, CharacterCreateGenderButtonFemale }
    elseif row == "race" then
        return { _G["CharacterCreateRaceButton" .. (CharacterCreate.selectedRace or 1)] }
    elseif row == "class" then
        return { _G["CharacterCreateClassButton" .. (CharacterCreate.selectedClass or 1)] }
    elseif type(row) == "number" then
        local frame = "CharacterCustomizationButtonFrame" .. row
        return { _G[frame .. "LeftButton"], _G[frame .. "RightButton"] }
    end
    return {}
end

local createStatus
local function lightRow()
    for _, row in ipairs({ "gender", "race", "class", 1, 2, 3, 4, 5 }) do
        for _, button in ipairs(rowButtons(row)) do
            if button then button:UnlockHighlight() end
        end
    end
    for i = 1, MAX_RACES do _G["CharacterCreateRaceButton" .. i]:UnlockHighlight() end
    for i = 1, MAX_CLASSES_PER_RACE do _G["CharacterCreateClassButton" .. i]:UnlockHighlight() end
    local rows = createRows()
    if createRow > #rows then createRow = #rows end
    local row = rows[createRow]
    for _, button in ipairs(rowButtons(row)) do
        if button then button:LockHighlight() end
    end
    if createStatus then
        if row == "name" then
            createStatus:SetText("Name: A to type it, then Enter to create. Enter with no name goes back to the pad")
        elseif type(row) == "number" then
            createStatus:SetText(_G["CharacterCustomizationButtonFrame" .. row .. "Text"]:GetText() or "")
        else
            createStatus:SetText(row == "gender" and "Gender" or row == "race" and "Race" or "Class")
        end
    end
end

local function stepChoice(count, current, step, usable)
    local index = current
    for _ = 1, count do
        index = index + step
        if index < 1 then index = count elseif index > count then index = 1 end
        if usable(index) then return index end
    end
end

local function changeRow(step)
    local row = createRows()[createRow]
    if row == "gender" then
        PlaySound("gsCharacterCreationClass")
        SetCharacterGender(GetSelectedSex() == SEX_MALE and SEX_FEMALE or SEX_MALE)
    elseif row == "race" then
        local index = stepChoice(CharacterCreate.numRaces, CharacterCreate.selectedRace or 1, step,
            function(i) return _G["CharacterCreateRaceButton" .. i].enable end)
        if index then
            local button = _G["CharacterCreateRaceButton" .. index]
            button:SetChecked(1)
            CharacterRace_OnClick(button, index)
        end
    elseif row == "class" then
        local index = stepChoice(CharacterCreate.numClasses, CharacterCreate.selectedClass or 1, step,
            function(i) return _G["CharacterCreateClassButton" .. i].enable end)
        if index then CharacterClass_OnClick(index) end
    elseif type(row) == "number" then
        if step < 0 then CharacterCustomization_Left(row) else CharacterCustomization_Right(row) end
    end
    lightRow()
end

local characterCreateShow = CharacterCreate_OnShow
CharacterCreate_OnShow = function(...)
    characterCreateShow(...)
    CharacterCreateNameEdit:ClearFocus()
    -- Focus can also be handed out after OnShow, so clear it again on the next frame.
    local timer = CreateFrame("Frame", nil, CharacterCreate)
    timer:SetScript("OnUpdate", function(self)
        self:SetScript("OnUpdate", nil)
        CharacterCreateNameEdit:ClearFocus()
    end)
    createRow = 1
    lightRow()
end

-- The name box would take every key as soon as the screen opens, so it only gets the keyboard on A.
-- Escape in it gives the pad back instead of leaving the screen.
if CharacterCreateNameEdit.SetAutoFocus then CharacterCreateNameEdit:SetAutoFocus(false) end
CharacterCreateNameEdit:SetScript("OnEscapePressed", function(self) self:ClearFocus() end)
-- A focused box keeps the pad's keys too, so Enter with no name also gives them back.
CharacterCreateNameEdit:SetScript("OnEnterPressed", function(self)
    if self:GetText() == "" then
        self:ClearFocus()
    else
        CharacterCreate_Okay()
    end
end)

-- Tapping or clicking anywhere else on the screen, or on any of its buttons, also leaves the box.
for _, name in ipairs({ "CharacterCreateFrame_OnMouseDown", "CharacterRace_OnClick", "CharacterClass_OnClick",
    "SetCharacterGender", "CharacterCustomization_Left", "CharacterCustomization_Right", "CharacterCreate_Randomize" }) do
    local original = _G[name]
    if original then
        _G[name] = function(...)
            CharacterCreateNameEdit:ClearFocus()
            return original(...)
        end
    end
end

local characterCreateKey = CharacterCreate_OnKeyDown
CharacterCreate_OnKeyDown = function(key)
    local mapped = translate(key)
    if not mapped then return end
    local rows = createRows()
    if mapped == "UP" then
        createRow = createRow > 1 and createRow - 1 or #rows
        lightRow()
    elseif mapped == "DOWN" then
        createRow = createRow < #rows and createRow + 1 or 1
        lightRow()
    elseif mapped == "LEFT" then
        changeRow(-1)
    elseif mapped == "RIGHT" then
        changeRow(1)
    elseif mapped == "PAD_X" then
        CharacterCreate_Randomize()
    elseif mapped == "PAD_L1" or mapped == "PAD_R1" then
        SetCharacterCreateFacing(GetCharacterCreateFacing() + (mapped == "PAD_L1" and -20 or 20))
    elseif mapped == "ENTER" and (rows[createRow] == "name" or CharacterCreateNameEdit:GetText() == "") then
        createRow = #rows
        lightRow()
        CharacterCreateNameEdit:SetFocus()
    else
        characterCreateKey(mapped)
    end
end
hint(CharacterCreate, "D-pad  Choose and change       A  Name, then create       Hold Select  Keyboard       X  Random look       L1 R1  Turn       B  Back")
createStatus = hint(CharacterCreate, "")
if createStatus then createStatus:SetPoint("TOP", CharacterCreate, "TOP", 0, -30) end

-- Realm list: the D-pad moves through realms, L1 and R1 switch tabs.
local function selectRealm(step)
    local category = RealmList.selectedCategory or 1
    local count = GetNumRealms(category)
    if count == 0 then return end
    local current = RealmList.currentRealm or 0
    local index = current + step
    if index < 1 then index = count elseif index > count then index = 1 end
    RealmList.currentRealm = index
    RealmList.selectedName = GetRealmInfo(category, index)
    local offset = RealmList.offset or 0
    if index <= offset or index > offset + MAX_REALMS_DISPLAYED then
        local top = math.max(0, math.min(index - 1, count - MAX_REALMS_DISPLAYED))
        RealmListScrollFrameScrollBar:SetValue(top * REALM_BUTTON_HEIGHT)
    end
    RealmListUpdate()
end

local function switchRealmTab(step)
    local tabs = 0
    while _G["RealmListTab" .. (tabs + 1)] and _G["RealmListTab" .. (tabs + 1)]:IsShown() do
        tabs = tabs + 1
    end
    if tabs < 2 then return end
    local index = RealmList.selectedCategory or 1
    for _ = 1, tabs do
        index = index + step
        if index < 1 then index = tabs elseif index > tabs then index = 1 end
        local tab = _G["RealmListTab" .. index]
        if not tab.disabled then
            RealmListTab_OnClick(tab)
            return
        end
    end
end

local realmListKey = RealmList_OnKeyDown
RealmList_OnKeyDown = function(key)
    local mapped = translate(key)
    if not mapped then return end
    if mapped == "UP" or mapped == "LEFT" then
        selectRealm(-1)
    elseif mapped == "DOWN" or mapped == "RIGHT" then
        selectRealm(1)
    elseif mapped == "PAD_L1" then
        switchRealmTab(-1)
    elseif mapped == "PAD_R1" then
        switchRealmTab(1)
    else
        realmListKey(mapped)
    end
end
hint(RealmList, "D-pad  Choose       L1 R1  Tabs       A  Accept       B  Cancel")

-- Dialogs, the authenticator prompt, the account picker and cinematics: A and B act as Enter and Escape.
wrap("GlueDialog_OnKeyDown")
wrap("TokenEntryOkayButton_OnKeyDown", true)
wrap("WoWAccountSelect_OnKeyDown", true)
wrap("CinematicsFrame_OnKeyDown")
wrap("ChangedOptionsDialog_OnKeyDown", true)
