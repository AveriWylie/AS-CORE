--[[
	Campfire: the hub's hero piece. Fallen log, rock ring, firewood, and the fire
	itself as an emitter plus a light.

	Paste into Studio's command bar. Re-running makes a SECOND Camp, so delete the
	old one first. Once the camp is hand-tuned in the viewport this script stops
	being the source of truth; dump the layout instead of re-running it.

	The fire's light Range is what decides how much forest is visible, so it and
	Lighting.FogEnd are the two numbers that set the whole composition.
]]

-- where the camp sits on your floor
local CENTER = Vector3.new(0, 0.5, 0)   

local camp = Instance.new("Model"); camp.Name = "Camp"; camp.Parent = workspace

-- u instantiate smth denoted by function
local function part(name, shape, size, cf, colour, material)  
	local p = Instance.new("Part")
	p.Name, p.Shape, p.Anchored, p.Size, p.CFrame = name, shape, true, size, cf
	p.Color, p.Material = colour, material
	p.TopSurface, p.BottomSurface = Enum.SurfaceType.Smooth, Enum.SurfaceType.Smooth
	p.Parent = camp
	return p
end

-- the fallen log: long cylinder, tipped on its side, set back from the fire
part("Log", Enum.PartType.Cylinder, Vector3.new(17, 4, 4),
	CFrame.new(CENTER + Vector3.new(-2, 1.6, -7)) * CFrame.Angles(0, math.rad(8), 0),
	Color3.fromRGB(70, 55, 45), Enum.Material.Wood)

-- ring of rocks, uneven so it doesn't read as a circle of spheres
for i = 1, 9 do
	local a = (i / 9) * math.pi * 2 + math.random() * 0.2
	local r = 3.6 + math.random() * 0.5
	local s = 1.1 + math.random() * 0.9
	part("Rock" .. i, Enum.PartType.Ball, Vector3.new(s, s * 0.75, s * 1.1),
		CFrame.new(CENTER + Vector3.new(math.cos(a) * r, 0.2, math.sin(a) * r))
			* CFrame.Angles(math.random(), math.random(), math.random()),
		Color3.fromRGB(85, 82, 78), Enum.Material.Slate)
end

-- firewood: a few short logs leaning into the middle
for i = 1, 4 do
	local a = (i / 4) * math.pi * 2
	part("Wood" .. i, Enum.PartType.Cylinder, Vector3.new(4.5, 0.9, 0.9),
		CFrame.new(CENTER + Vector3.new(math.cos(a) * 1.1, 0.6, math.sin(a) * 1.1))
			* CFrame.Angles(0, -a, math.rad(28)),
		Color3.fromRGB(45, 35, 28), Enum.Material.Wood)
end

-- the fire itself: one invisible emitter part, plus the light
local fire = part("Fire", Enum.PartType.Ball, Vector3.new(1, 1, 1),
	CFrame.new(CENTER + Vector3.new(0, 0.8, 0)), Color3.new(), Enum.Material.SmoothPlastic)

fire.Transparency, fire.CanCollide = 1, false

local flame = Instance.new("ParticleEmitter", fire)
flame.Color = ColorSequence.new(Color3.fromRGB(255, 190, 90), Color3.fromRGB(190, 60, 20))

flame.Transparency = NumberSequence.new({
	NumberSequenceKeypoint.new(0, 0.3), NumberSequenceKeypoint.new(0.7, 0.6), NumberSequenceKeypoint.new(1, 1)})

flame.Size = NumberSequence.new({
	NumberSequenceKeypoint.new(0, 1.6), NumberSequenceKeypoint.new(0.6, 2.4), NumberSequenceKeypoint.new(1, 0.4)})

flame.Lifetime = NumberRange.new(0.5, 1.0)
flame.Rate, flame.Speed = 26, NumberRange.new(3, 6)
flame.Acceleration = Vector3.new(0, 9, 0)
flame.SpreadAngle = Vector2.new(14, 14)
flame.LightEmission, flame.LightInfluence = 1, 0

local light = Instance.new("PointLight", fire)
light.Color, light.Range, light.Brightness = Color3.fromRGB(255, 165, 80), 38, 3
