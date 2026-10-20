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
