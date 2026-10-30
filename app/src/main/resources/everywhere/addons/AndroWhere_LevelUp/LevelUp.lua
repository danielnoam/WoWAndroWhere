-- Level Up Banner: a later-expansion style "Level N" banner on PLAYER_LEVEL_UP, drawn with the client's own fonts and solid colours only.

local FADE_IN, HOLD, FADE_OUT = 0.4, 4, 1.2
local WIDTH, HEIGHT = 560, 92

local banner = CreateFrame("Frame", "AndroWhereLevelUpBanner", UIParent)
banner:SetSize(WIDTH, HEIGHT)
banner:SetPoint("TOP", 0, -150)
banner:SetFrameStrata("HIGH")
banner:Hide()

local function band(point, alphaFrom, alphaTo, height, y, r, g, b)
    local t = banner:CreateTexture(nil, "BACKGROUND")
    t:SetTexture(1, 1, 1, 1)
    t:SetSize(WIDTH / 2, height)
    t:SetPoint(point, banner, "CENTER", 0, y)
    t:SetGradientAlpha("HORIZONTAL", r, g, b, alphaFrom, r, g, b, alphaTo)
    return t
end

band("RIGHT", 0, 0.75, HEIGHT, 0, 0, 0, 0)
band("LEFT", 0.75, 0, HEIGHT, 0, 0, 0, 0)
for _, y in ipairs({ HEIGHT / 2, -HEIGHT / 2 }) do
    band("RIGHT", 0, 1, 2, y, 1, 0.82, 0)
    band("LEFT", 1, 0, 2, y, 1, 0.82, 0)
end

local heading = banner:CreateFontString(nil, "ARTWORK", "GameFontHighlightLarge")
heading:SetPoint("TOP", 0, -12)
heading:SetText("You've reached")

local level = banner:CreateFontString(nil, "ARTWORK")
level:SetFont(STANDARD_TEXT_FONT or "Fonts\\FRIZQT__.TTF", 34, "OUTLINE")
level:SetTextColor(1, 0.82, 0)
level:SetShadowOffset(2, -2)
level:SetPoint("TOP", heading, "BOTTOM", 0, -6)

local talents = banner:CreateFontString(nil, "ARTWORK", "GameFontNormal")
talents:SetPoint("TOP", banner, "BOTTOM", 0, -6)

local elapsed = 0
banner:SetScript("OnUpdate", function(self, delta)
    elapsed = elapsed + delta
    if elapsed < FADE_IN then
        self:SetAlpha(elapsed / FADE_IN)
    elseif elapsed < FADE_IN + HOLD then
        self:SetAlpha(1)
    elseif elapsed < FADE_IN + HOLD + FADE_OUT then
        self:SetAlpha(1 - (elapsed - FADE_IN - HOLD) / FADE_OUT)
    else
        self:Hide()
    end
end)

local function show(newLevel, talentPoints)
    level:SetText(LEVEL .. " " .. newLevel)
    if talentPoints and talentPoints > 0 then
        talents:SetText(talentPoints == 1 and "You have a new talent point" or ("You have " .. talentPoints .. " new talent points"))
    else
        talents:SetText("")
    end
    elapsed = 0
    banner:SetAlpha(0)
    banner:Show()
end

banner:RegisterEvent("PLAYER_LEVEL_UP")
banner:SetScript("OnEvent", function(_, _, newLevel, _, _, talentPoints)
    show(newLevel, talentPoints)
end)

SLASH_ANDROWHERELEVELUP1 = "/levelbanner"
SlashCmdList.ANDROWHERELEVELUP = function()
    show(UnitLevel("player"), 0)
end
