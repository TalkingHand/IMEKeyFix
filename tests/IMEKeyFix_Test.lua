-- Test helpers for pzt.conf (installed into the test copy of the mod only).
-- The checks themselves are `check` steps in pzt.conf; these only drive the UI.
IMEKeyFixTest = IMEKeyFixTest or {}
local T = IMEKeyFixTest

local function log(msg)
    print("[IMEKeyFix(Test)] " .. msg)
end

-- one line per check point, so console.txt shows the state even when a check passes
function T.state(label)
    log(label .. ": " .. IMEKeyFix.status())
end

function T.openBox()
    local box = ISTextEntryBox:new("", 200, 200, 200, 24)
    box:initialise()
    box:instantiate()
    box:addToUIManager()
    box:focus()
    T.box = box
end

function T.closeBox()
    if T.box then
        T.box:unfocus()
        T.box:removeFromUIManager()
        T.box = nil
    end
end

local function window(key)
    local p = ISEntityUI.players and ISEntityUI.players[0]
    local w = p and p.windows[key]
    return w and w.instance
end

local function searchBox(key)
    local w = window(key)
    if not w then return nil end
    local panel = w.handCraftPanel or w.BuildPanel
    return panel and panel.recipesPanel and panel.recipesPanel.recipeFilterPanel
        and panel.recipesPanel.recipeFilterPanel.searchEntryBox
end

function T.openCraft()
    ISEntityUI.OpenHandcraftWindow(getPlayer(), nil)
end

function T.openBuild()
    ISEntityUI.OpenBuildWindow(getPlayer(), nil)
end

-- focus the window's recipe search box the way a click does (UITextBox2.onMouseDown)
function T.focusSearch(key)
    local box = searchBox(key)
    if not box then
        PZTest.fail("no search box in " .. key)
        return
    end
    box:focus()
    log(key .. " search box focused: " .. tostring(box:isFocused()))
end

function T.hasSearch(key)
    return searchBox(key) ~= nil
end

function T.closeWindow(key)
    local w = window(key)
    if w then w:close() end
end

-- Enter during composition: Enter and the IME's committed syllable arrive in one frame,
-- Enter first (as Windows sends them). The box must submit "ab" plus the syllable.
T.syllable = "ㅋ"

function T.openEnterBox()
    T.closeBox()
    T.openBox()
    T.entered = nil
    T.box.onCommandEntered = function(self)
        T.entered = self:getText()
        log("onCommandEntered text=[" .. tostring(T.entered) .. "]")
        self:unfocus()
    end
    T.box:setText("ab")
end

function T.fakeEnter()
    IMEKeyFix.fakeEnterDuringComposition(T.syllable)
end

function T.enteredWithSyllable()
    -- cursor position after setText is not the point; the syllable must be in the submitted text
    return T.entered == "ab" .. T.syllable or T.entered == T.syllable .. "ab"
end

-- Composition shown in the box (fake IME via IMEKeyFix.setTestComposition / fakeInput)
T.J = { n = "ㄴ", na = "나", k = "ㅋ", d = "ㄷ" }

function T.openPreeditBox()
    T.closeBox()
    T.openBox()
    T.seen = nil
    T.box.onTextChange = function(self)
        T.seen = self:getInternalText()
    end
    T.box.onOtherKey = function(self, key)
        if key == 1 then self:unfocus() end
    end
    T.box:setText("ab")
    T.box.javaObject:setCursorPos(2)
end

function T.compose(key)
    IMEKeyFix.setTestComposition(key and T.J[key] or "")
end

-- internal text is "ab" followed by the listed T.J keys
local function expected(...)
    local s = "ab"
    for _, k in ipairs({...}) do s = s .. T.J[k] end
    return s
end

function T.internalIs(...)
    local want, got = expected(...), T.box:getInternalText()
    if got ~= want then log("internal text [" .. tostring(got) .. "] want [" .. want .. "]") end
    return got == want
end

function T.seenIs(...)
    local want = expected(...)
    if T.seen ~= want then log("onTextChange saw [" .. tostring(T.seen) .. "] want [" .. want .. "]") end
    return T.seen == want
end

function T.commit(key)
    IMEKeyFix.fakeInput(0, T.J[key], "")
end

function T.fakeEsc()
    IMEKeyFix.fakeInput(256, "", "")
end

function T.searchIs(key, j)
    local box = searchBox(key)
    local got = box and box:getInternalText()
    log(key .. " search internal text [" .. tostring(got) .. "]")
    return got == T.J[j]
end
