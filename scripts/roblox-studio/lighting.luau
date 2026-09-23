--[[
	Night forest lighting. Run after the shell exists.

	FogEnd is the boundary: it must close well inside the shell's W and D, or the
	player sees clear space and then hits an invisible wall. With a 300 shell,
	100 to 130 is the range that works.

	The fire's PointLight Range and this FogEnd are the whole composition. Turn
	those two before touching anything else.
]]

local L = game:GetService("Lighting")

L.Technology = Enum.Technology.Future
L.ClockTime = 3                                    -- night
L.Brightness = 0.6                                 -- sun/moon contribution, kept low
L.ExposureCompensation = 0.4
L.Ambient = Color3.fromRGB(18, 22, 30)             -- shadowed areas: cold, nearly black
L.OutdoorAmbient = Color3.fromRGB(28, 34, 46)
L.EnvironmentDiffuseScale = 0.25
L.EnvironmentSpecularScale = 0.25
L.GlobalShadows = true

L.FogEnd = 120
L.FogStart = 25
L.FogColor = Color3.fromRGB(22, 28, 36)

local atmos = L:FindFirstChildOfClass("Atmosphere") or Instance.new("Atmosphere", L)
atmos.Density = 0.42
atmos.Offset = 0.1
atmos.Haze = 2.4
atmos.Glare = 0.15
atmos.Color = Color3.fromRGB(170, 185, 200)
atmos.Decay = Color3.fromRGB(80, 100, 130)

local bloom = L:FindFirstChildOfClass("BloomEffect") or Instance.new("BloomEffect", L)
bloom.Intensity, bloom.Size, bloom.Threshold = 0.8, 20, 1.4

local cc = L:FindFirstChildOfClass("ColorCorrectionEffect") or Instance.new("ColorCorrectionEffect", L)
cc.Brightness, cc.Contrast, cc.Saturation = -0.02, 0.12, -0.12
cc.TintColor = Color3.fromRGB(215, 225, 240)

-- the fire is the only real light source, so let it cast shadows
local fire = workspace:FindFirstChild("Camp") and workspace.Camp:FindFirstChild("Fire")
if fire then
	local light = fire:FindFirstChildOfClass("PointLight")
	if light then
		light.Shadows = true
		light.Range, light.Brightness = 34, 4
	end
end
